import contextlib
from io import StringIO
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import import_complexes as script


FIXTURE = Path(__file__).parent / "fixtures/sample-run.log"


class ImportComplexesTest(unittest.TestCase):
    def test_mapping_has_254_unique_codes_in_mapping_order(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)
        self.assertEqual(254, len(codes))
        self.assertEqual(254, len(set(codes)))
        self.assertEqual("1168011000", codes[0])
        self.assertEqual("4159510200", codes[-1])
        self.assertIn("1168010300", codes)

    def test_fixture_parses_every_status_and_discards_secrets(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:4]
        rows = script.parse_results(FIXTURE.read_text(), codes, 0)
        self.assertEqual(
            ["SUCCESS", "PARTIAL", "LIST_FAILED", "EMPTY"],
            [row["status"] for row in rows],
        )
        self.assertEqual("요청 시간 초과", rows[1]["reason"])
        self.assertEqual("API 응답 코드 04", rows[2]["reason"])
        self.assertNotIn("VERY_SECRET", json.dumps(rows))
        self.assertNotIn("https://", json.dumps(rows))

    def test_missing_result_is_recorded_as_failure(self):
        rows = script.parse_results("", ["1168011000"], 1)
        self.assertEqual("PROCESS_FAILED", rows[0]["status"])
        self.assertIn("종료 코드 1", rows[0]["reason"])

    def test_mapping_failure_records_safe_complex_code_and_category(self):
        output = (
            "공동주택 단지 적재 진행: 법정동 1/1, 코드=1168011000, "
            "상태=PARTIAL, 신규=0, 건너뛰기=0, 실패=1\n"
            "공동주택 단지 적재에 실패했습니다. 법정동 코드=1168011000, "
            "단지코드=A12345678, 유형=MAPPING, 사유=비공개 오류 상세"
        )
        row = script.parse_results(output, ["1168011000"], 0)[0]
        self.assertEqual("매핑 검증 실패", row["reason"])
        self.assertEqual(["A12345678"], row["failed_complex_codes"])
        self.assertNotIn("비공개", json.dumps(row, ensure_ascii=False))

    def test_mapping_failure_records_validation_type_without_raw_value(self):
        output = (
            "공동주택 단지 적재 진행: 법정동 1/1, 코드=1168011000, "
            "상태=PARTIAL, 신규=0, 건너뛰기=0, 실패=1\n"
            "공동주택 단지 적재에 실패했습니다. 법정동 코드=1168011000, "
            "단지코드=A12345678, 유형=MAPPING, "
            "사유=사용승인일을 날짜로 변환할 수 없습니다: PRIVATE_VALUE"
        )
        row = script.parse_results(output, ["1168011000"], 0)[0]
        self.assertEqual("사용승인일 형식 오류", row["reason"])
        self.assertNotIn("PRIVATE_VALUE", json.dumps(row, ensure_ascii=False))

    def test_known_source_gap_is_accounted_separately(self):
        code = "1168010300"
        known = script.known_source_gaps(script.DEFAULT_SOURCE_GAPS, {code})
        output = (
            "공동주택 단지 적재 진행: 법정동 1/1, 코드=1168010300, "
            "상태=PARTIAL, 신규=0, 건너뛰기=20, 실패=1\n"
            "공동주택 단지 적재에 실패했습니다. 법정동 코드=1168010300, "
            "단지코드=A40010008, 유형=MAPPING, 사유=세대수가 없습니다."
        )
        row = script.parse_results(output, [code], 0, known)[0]
        self.assertEqual("SOURCE_GAP", row["status"])
        self.assertEqual(1, row["failed"])
        self.assertEqual(["A40010008"], row["failed_complex_codes"])
        self.assertIn("원본 결측", row["reason"])

    def test_known_source_gap_with_another_failure_remains_partial(self):
        code = "1168010300"
        known = script.known_source_gaps(script.DEFAULT_SOURCE_GAPS, {code})
        output = (
            "공동주택 단지 적재 진행: 법정동 1/1, 코드=1168010300, "
            "상태=PARTIAL, 신규=0, 건너뛰기=20, 실패=2\n"
            "공동주택 단지 적재에 실패했습니다. 법정동 코드=1168010300, "
            "단지코드=A40010008, 유형=MAPPING, 사유=세대수가 없습니다.\n"
            "공동주택 단지 적재에 실패했습니다. 법정동 코드=1168010300, "
            "단지코드=A40010009, 유형=API, 사유=응답 코드 04"
        )
        row = script.parse_results(output, [code], 0, known)[0]
        self.assertEqual("PARTIAL", row["status"])

    def test_source_gap_can_be_rechecked_on_request(self):
        code = "1168010300"
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": [code]}]}))
            manifest = Path(directory) / "import.jsonl"
            script.append_rows(manifest, [{"legal_dong_code": code,
                                           "status": "SOURCE_GAP", "created": 0,
                                           "skipped": 20, "failed": 1,
                                           "reason": "확인된 원본 결측: A40010008"}])
            with contextlib.redirect_stdout(StringIO()) as default_output:
                self.assertEqual(0, script.main([
                    "--dry-run", "--mapping", str(mapping), "--manifest", str(manifest),
                ]))
            with contextlib.redirect_stdout(StringIO()) as recheck_output:
                self.assertEqual(0, script.main([
                    "--dry-run", "--mapping", str(mapping), "--manifest", str(manifest),
                    "--recheck-source-gaps",
                ]))
        self.assertIn("대기=0", default_output.getvalue())
        self.assertIn(f"배치 1: {code}", recheck_output.getvalue())

    def test_resume_skips_success_and_empty_and_retries_failures(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:4]
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "import.jsonl"
            script.append_rows(manifest, script.parse_results(FIXTURE.read_text(), codes, 0))
            latest = script.latest_manifest(manifest, set(codes))
            pending = [code for code in codes if latest.get(code, {}).get("status") not in script.SUCCESS]
            self.assertEqual(codes[1:3], pending)
            # A later successful attempt supersedes the earlier partial result.
            script.append_rows(manifest, [{"legal_dong_code": codes[1], "status": "SUCCESS",
                                           "created": 0, "skipped": 2, "failed": 0,
                                           "reason": None}])
            latest = script.latest_manifest(manifest, set(codes))
            self.assertEqual([codes[2]], [code for code in codes
                                         if latest.get(code, {}).get("status") not in script.SUCCESS])

    def test_dry_run_does_not_call_runner(self):
        with patch.object(script, "run_batch", side_effect=AssertionError("executed")):
            with contextlib.redirect_stdout(StringIO()) as output:
                self.assertEqual(0, script.main(["--dry-run", "--batch-size", "5"]))
        self.assertEqual(51, output.getvalue().count("배치 "))
        self.assertIn("전체=254 선택=254 대기=254", output.getvalue())

    def test_dry_run_prioritizes_unattempted_codes_over_failed_retries(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:3]
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": codes}]}))
            manifest = Path(directory) / "import.jsonl"
            script.append_rows(manifest, [{
                "legal_dong_code": codes[0], "status": "LIST_FAILED",
                "created": 0, "skipped": 0, "failed": 0,
                "reason": "API 응답 코드 04",
            }])
            with contextlib.redirect_stdout(StringIO()) as output:
                result = script.main([
                    "--dry-run", "--mapping", str(mapping),
                    "--manifest", str(manifest), "--batch-size", "1",
                ])
        self.assertEqual(0, result)
        lines = [line for line in output.getvalue().splitlines() if line.startswith("배치 ")]
        self.assertEqual([
            f"배치 1: {codes[1]}",
            f"배치 2: {codes[2]}",
            f"배치 3: {codes[0]}",
        ], lines)

    def test_run_writes_per_dong_results_and_resumes_in_order(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:4]
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": codes}]}))
            manifest = Path(directory) / "import.jsonl"
            lines = FIXTURE.read_text().splitlines()
            first_output = "\n".join(lines[:3])
            second_output = "\n".join(lines[3:])
            with patch.object(script, "run_batch", side_effect=[(0, first_output),
                                                               (0, second_output)]) as runner:
                with contextlib.redirect_stdout(StringIO()):
                    self.assertEqual(1, script.main(["--run", "--mapping", str(mapping),
                                                     "--manifest", str(manifest),
                                                     "--batch-size", "2"]))
            self.assertEqual(
                [(codes[:2], mapping, 600), (codes[2:], mapping, 600)],
                [call.args for call in runner.call_args_list],
            )
            self.assertEqual(4, len(manifest.read_text().splitlines()))
            with patch.object(script, "run_batch", return_value=(0, "\n".join([
                f"공동주택 단지 적재 진행: 법정동 1/2, 코드={codes[1]}, 상태=SUCCESS, 신규=0, 건너뛰기=1, 실패=0",
                f"공동주택 단지 적재 진행: 법정동 2/2, 코드={codes[2]}, 상태=EMPTY, 신규=0, 건너뛰기=0, 실패=0",
            ]))) as runner:
                with contextlib.redirect_stdout(StringIO()):
                    self.assertEqual(0, script.main(["--run", "--mapping", str(mapping),
                                                     "--manifest", str(manifest),
                                                     "--batch-size", "2"]))
                runner.assert_called_once_with(codes[1:3], mapping, 600)

    def test_run_uses_local_api_settings_and_exits_without_web_server(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "DATA_GO_KR_SERVICE_KEY=test-key\n"
                "DATA_GO_KR_APT_LIST_BASE_URL=https://example.test/list\n"
                "DATA_GO_KR_APT_BASIC_INFO_BASE_URL=https://example.test/basic\n"
            )
            with patch.object(script, "ROOT", Path(directory)), \
                 patch.object(script.subprocess, "run") as process, \
                 patch.dict(os.environ, {}, clear=True):
                process.return_value.returncode = 0
                process.return_value.stdout = ""
                script.run_batch(["1168011000"], Path(directory) / "mapping.json", 20)
                kwargs = process.call_args.kwargs
                self.assertIn("--spring.main.web-application-type=none", process.call_args.args[0][2])
                self.assertEqual("test-key", kwargs["env"]["DATA_GO_KR_SERVICE_KEY"])
                self.assertEqual("true", kwargs["env"]["DATA_GO_KR_APT_LIST_ENABLED"])
                self.assertEqual(20, kwargs["timeout"])

    def test_run_stops_after_repeated_list_failures(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:4]
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": codes}]}))
            manifest = Path(directory) / "import.jsonl"
            def failed_result(batch, path, timeout):
                return 0, (f"공동주택 단지 적재 진행: 법정동 1/1, "
                           f"코드={batch[0]}, 상태=LIST_FAILED, "
                           "신규=0, 건너뛰기=0, 실패=0")
            with patch.object(script, "run_batch", side_effect=failed_result) as runner:
                with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                    self.assertEqual(1, script.main([
                        "--run", "--mapping", str(mapping), "--manifest", str(manifest),
                        "--batch-size", "1", "--max-consecutive-list-failures", "2",
                    ]))
            self.assertEqual(2, runner.call_count)
            self.assertEqual(2, len(manifest.read_text().splitlines()))

    def test_run_stops_when_basic_and_list_failures_repeat_without_saves(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:4]
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": codes}]}))
            manifest = Path(directory) / "import.jsonl"

            def failed_result(batch, path, timeout):
                status = "LIST_FAILED" if batch[0] == codes[1] else "PARTIAL"
                failed = 0 if status == "LIST_FAILED" else 1
                return 0, (f"공동주택 단지 적재 진행: 법정동 1/1, "
                           f"코드={batch[0]}, 상태={status}, "
                           f"신규=0, 건너뛰기=0, 실패={failed}")

            with patch.object(script, "run_batch", side_effect=failed_result) as runner:
                with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                    result = script.main([
                        "--run", "--mapping", str(mapping), "--manifest", str(manifest),
                        "--batch-size", "1", "--max-consecutive-api-failures", "3",
                    ])

            self.assertEqual(1, result)
            self.assertEqual(3, runner.call_count)
            self.assertEqual(3, len(manifest.read_text().splitlines()))

    def test_quota_error_stops_before_another_batch(self):
        codes = script.mapping_codes(script.DEFAULT_MAPPING)[:2]
        with tempfile.TemporaryDirectory() as directory:
            mapping = Path(directory) / "mapping.json"
            mapping.write_text(json.dumps({"zones": [{"dongCodes": codes}]}))
            manifest = Path(directory) / "import.jsonl"
            output = "ComplexApiQuotaExceededException: 공동주택 기본정보 API 호출 한도를 초과했습니다."
            with patch.object(script, "run_batch", return_value=(1, output)) as runner:
                with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                    result = script.main([
                        "--run", "--mapping", str(mapping), "--manifest", str(manifest),
                        "--batch-size", "1",
                    ])
            self.assertEqual(3, result)
            runner.assert_called_once()


if __name__ == "__main__":
    unittest.main()
