import contextlib
import fcntl
from io import StringIO
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import complete_import
import import_complexes


class CompleteImportTest(unittest.TestCase):
    def test_resumes_after_progress_and_stops_when_complete(self):
        codes = ["1168011000", "1168011100"]
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "run.jsonl"

            def importer(argv):
                latest = import_complexes.latest_manifest(manifest, set(codes))
                next_code = next(code for code in codes if code not in latest)
                import_complexes.append_rows(manifest, [{
                    "legal_dong_code": next_code, "status": "SUCCESS",
                    "created": 1, "skipped": 0, "failed": 0, "reason": None,
                }])
                return 1

            sleeper = Mock()
            with contextlib.redirect_stdout(StringIO()):
                result = complete_import.run_until_complete(
                    manifest, codes, 2, 120, 3, 10, importer, sleeper,
                )
            self.assertEqual(0, result)
            sleeper.assert_called_once_with(120)

    def test_stops_after_no_progress(self):
        with tempfile.TemporaryDirectory() as directory:
            sleeper = Mock()
            importer = Mock(return_value=1)
            with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                result = complete_import.run_until_complete(
                    Path(directory) / "run.jsonl", ["1168011000"],
                    1, 120, 2, 10, importer, sleeper,
                )
            self.assertEqual(1, result)
            self.assertEqual(2, importer.call_count)
            sleeper.assert_called_once_with(120)

    def test_forwards_list_failure_limit_for_full_pending_scan(self):
        with tempfile.TemporaryDirectory() as directory:
            importer = Mock(return_value=1)
            with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                result = complete_import.run_until_complete(
                    Path(directory) / "run.jsonl", ["1168011000"],
                    1, 0, 1, 1, importer,
                    max_consecutive_list_failures=20,
                    max_consecutive_api_failures=20,
                )
            self.assertEqual(1, result)
            self.assertEqual(
                "20",
                importer.call_args.args[0][
                    importer.call_args.args[0].index("--max-consecutive-list-failures") + 1
                ],
            )
            self.assertEqual(
                "20",
                importer.call_args.args[0][
                    importer.call_args.args[0].index("--max-consecutive-api-failures") + 1
                ],
            )

    def test_no_progress_waits_increase_until_cap(self):
        with tempfile.TemporaryDirectory() as directory:
            sleeper = Mock()
            importer = Mock(return_value=1)
            with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                result = complete_import.run_until_complete(
                    Path(directory) / "run.jsonl", ["1168011000"],
                    1, 120, 6, 10, importer, sleeper,
                )
            self.assertEqual(1, result)
            self.assertEqual(
                [120, 240, 480, 900, 900],
                [call.args[0] for call in sleeper.call_args_list],
            )

    def test_stops_immediately_on_quota_error(self):
        with tempfile.TemporaryDirectory() as directory:
            sleeper = Mock()
            importer = Mock(return_value=3)
            with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                result = complete_import.run_until_complete(
                    Path(directory) / "run.jsonl", ["1168011000"],
                    1, 120, 3, 10, importer, sleeper,
                )
            self.assertEqual(3, result)
            importer.assert_called_once()
            sleeper.assert_not_called()

    def test_source_gap_counts_as_accounted_but_is_reported(self):
        code = "1168010300"
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "run.jsonl"
            import_complexes.append_rows(manifest, [{
                "legal_dong_code": code, "status": "SOURCE_GAP",
                "created": 0, "skipped": 20, "failed": 1,
                "reason": "확인된 원본 결측: A40010008",
            }])
            importer = Mock()
            with contextlib.redirect_stdout(StringIO()) as output:
                result = complete_import.run_until_complete(
                    manifest, [code], 1, 120, 3, 10, importer,
                )
            self.assertEqual(0, result)
            importer.assert_not_called()
            self.assertIn("원본 결측 법정동 1곳", output.getvalue())

    def test_second_supervisor_on_same_manifest_exits_before_importing(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "run.jsonl"
            lock_path = Path(str(manifest) + ".lock")
            with lock_path.open("a+") as existing_lock:
                fcntl.flock(existing_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                with patch.object(complete_import, "run_until_complete") as importer:
                    with contextlib.redirect_stderr(StringIO()) as errors:
                        result = complete_import.main([
                            "--manifest", str(manifest), "--max-rounds", "1",
                        ])
                self.assertEqual(2, result)
                importer.assert_not_called()
                self.assertIn("이미 실행 중", errors.getvalue())

    def test_partial_new_rows_count_as_progress(self):
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "run.jsonl"
            calls = 0

            def importer(argv):
                nonlocal calls
                calls += 1
                if calls == 1:
                    import_complexes.append_rows(manifest, [{
                        "legal_dong_code": "1168011000", "status": "PARTIAL",
                        "created": 2, "skipped": 0, "failed": 1,
                        "reason": "API 응답 코드 04",
                    }])
                return 1

            sleeper = Mock()
            with contextlib.redirect_stdout(StringIO()), contextlib.redirect_stderr(StringIO()):
                result = complete_import.run_until_complete(
                    manifest, ["1168011000"], 1, 120, 2, 10,
                    importer, sleeper,
                )
            self.assertEqual(1, result)
            self.assertEqual(3, calls)
            self.assertEqual(2, sleeper.call_count)


if __name__ == "__main__":
    unittest.main()
