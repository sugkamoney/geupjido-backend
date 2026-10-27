#!/usr/bin/env python3
"""Run the existing Spring manual complex importer in resumable small batches."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MAPPING = ROOT / "data/location/mapping/location-mapping.json"
DEFAULT_SOURCE_GAPS = ROOT / "data/complex/known-source-gaps.json"
CODE = re.compile(r"\d{10}\Z")
COMPLEX_CODE = re.compile(r"[A-Za-z0-9]{1,20}\Z")
PROGRESS = re.compile(
    r"공동주택 단지 적재 진행: 법정동 \d+/\d+, 코드=(\d{10}), "
    r"상태=(SUCCESS|PARTIAL|EMPTY|LIST_FAILED), "
    r"신규=(\d+), 건너뛰기=(\d+), 실패=(\d+)"
)
LIST_FAILURE = re.compile(r"공동주택 목록 조회 실패: 법정동 코드=(\d{10}), 사유=(.*)")
COMPLEX_FAILURE = re.compile(
    r"공동주택 단지 적재에 실패했습니다\. 법정동 코드=(\d{10}), "
    r"단지코드=([^,]+), 유형=([^,]+), 사유=(.*)"
)
SUCCESS = {"SUCCESS", "EMPTY"}
SOURCE_GAP = "SOURCE_GAP"
TERMINAL = SUCCESS | {SOURCE_GAP}
STATUSES = TERMINAL | {"PARTIAL", "LIST_FAILED", "PROCESS_FAILED"}
QUOTA_ERROR = re.compile(
    r"ComplexApiQuotaExceededException|(?:resultCode|응답 코드)[=: ]+22\b|호출 한도를 초과",
    re.I,
)
REQUIRED_ENV = (
    "DATA_GO_KR_SERVICE_KEY",
    "DATA_GO_KR_APT_LIST_BASE_URL",
    "DATA_GO_KR_APT_BASIC_INFO_BASE_URL",
)


def mapping_codes(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    codes = [code for zone in data["zones"] for code in zone["dongCodes"]]
    if not codes or any(not isinstance(code, str) or not CODE.fullmatch(code) for code in codes):
        raise ValueError("매핑에 유효하지 않은 법정동 코드가 있습니다")
    if len(codes) != len(set(codes)):
        raise ValueError("매핑에 중복된 법정동 코드가 있습니다")
    return codes


def known_source_gaps(path, valid_codes):
    data = json.loads(path.read_text(encoding="utf-8"))
    entries = data["gaps"]
    if not isinstance(entries, list):
        raise ValueError("원본 결측 목록은 배열이어야 합니다")
    known = {}
    for entry in entries:
        code = entry["legalDongCode"]
        complex_code = entry["complexCode"]
        reason = entry["reason"]
        if not CODE.fullmatch(code) or not COMPLEX_CODE.fullmatch(complex_code):
            raise ValueError("원본 결측 목록에 유효하지 않은 코드가 있습니다")
        if reason not in {"세대수 누락", "법정동 주소 누락"}:
            raise ValueError("원본 결측 목록에 허용되지 않은 사유가 있습니다")
        key = (code, complex_code)
        if key in known:
            raise ValueError("원본 결측 목록에 중복된 단지가 있습니다")
        if code in valid_codes:
            known[key] = reason
    return known


def latest_manifest(path, valid_codes):
    latest = {}
    if not path.exists():
        return latest
    with path.open(encoding="utf-8") as source:
        for number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"매니페스트 {number}행의 JSON이 잘못됐습니다") from exc
            code = row.get("legal_dong_code")
            if code not in valid_codes or row.get("status") not in STATUSES:
                raise ValueError(f"매니페스트 {number}행의 코드 또는 상태가 잘못됐습니다")
            latest[code] = row
    return latest


def reason_category(message):
    """Only fixed categories enter the manifest; raw API messages may contain keys."""
    if re.search(r"(?:응답 코드|resultCode|result code)[=: ]+0?4\b", message, re.I):
        return "API 응답 코드 04"
    if re.search(r"(?:응답 코드|resultCode|result code)[=: ]+22\b", message, re.I):
        return "API 호출 한도 초과 (22)"
    if re.search(r"timeout|timed out|시간 초과", message, re.I):
        return "요청 시간 초과"
    if re.search(r"connection|connect|연결", message, re.I):
        return "연결 실패"
    return "상세 사유는 실행 로그 확인"


def mapping_reason_category(message):
    """Keep only known validation names; never persist raw API values."""
    patterns = (
        (r"세대수가 없습니다", "세대수 누락"),
        (r"세대수를 정수로 변환할 수 없습니다", "세대수 정수 변환 실패"),
        (r"동 수를 정수로 변환할 수 없습니다", "동 수 정수 변환 실패"),
        (r"사용승인일을 날짜로 변환할 수 없습니다", "사용승인일 형식 오류"),
        (r"단지 코드.*비어 있을 수 없습니다", "단지 코드 누락"),
        (r"단지명.*비어 있을 수 없습니다", "단지명 누락"),
        (r"법정동 주소.*비어 있을 수 없습니다", "법정동 주소 누락"),
        (r"법정동 코드는 숫자 10자리여야 합니다", "법정동 코드 형식 오류"),
        (r"세대수는 0 이상이어야 합니다", "세대수 음수"),
        (r"동 수는 1 이상이어야 합니다", "동 수 0 이하"),
    )
    for pattern, category in patterns:
        if re.search(pattern, message):
            return category
    return "매핑 검증 실패"


def parse_results(output, codes, exit_code, source_gaps=None):
    source_gaps = source_gaps or {}
    expected = set(codes)
    progress = {}
    reasons = {}
    failed_complex_codes = {}
    failures_by_code = {}
    for line in output.splitlines():
        match = PROGRESS.search(line)
        if match and match.group(1) in expected:
            code, status, created, skipped, failed = match.groups()
            progress[code] = {
                "status": status,
                "created": int(created),
                "skipped": int(skipped),
                "failed": int(failed),
            }
        match = LIST_FAILURE.search(line)
        if match and match.group(1) in expected:
            reasons.setdefault(match.group(1), []).append(reason_category(match.group(2)))
        match = COMPLEX_FAILURE.search(line)
        if match and match.group(1) in expected:
            code, complex_code, failure_type, message = match.groups()
            category = reason_category(message)
            if category == "상세 사유는 실행 로그 확인":
                category = mapping_reason_category(message) if failure_type == "MAPPING" else "API 기타 오류"
            reasons.setdefault(match.group(1), []).append(category)
            if re.fullmatch(r"[A-Za-z0-9]{1,20}", complex_code):
                failed_complex_codes.setdefault(code, []).append(complex_code)
                failures_by_code.setdefault(code, []).append((complex_code, category))

    rows = []
    for code in codes:
        item = progress.get(code)
        if item is None:
            item = {"status": "PROCESS_FAILED", "created": 0, "skipped": 0, "failed": 0}
            reason = f"프로세스 종료 코드 {exit_code}; 법정동 결과 로그 없음"
        elif item["status"] in SUCCESS:
            reason = None
        else:
            categories = list(dict.fromkeys(reasons.get(code, [])))
            reason = "; ".join(categories) if categories else "상세 사유는 실행 로그 확인"
        failures = failures_by_code.get(code, [])
        if (item["status"] == "PARTIAL" and item["failed"] > 0
            and item["failed"] == len(failures)
            and all(source_gaps.get((code, complex_code)) == category
                    for complex_code, category in failures)):
            item = {**item, "status": SOURCE_GAP}
            reason = "확인된 원본 결측: " + ", ".join(
                complex_code for complex_code, _ in failures
            )
        rows.append({"legal_dong_code": code, **item, "reason": reason,
                     "failed_complex_codes": list(dict.fromkeys(failed_complex_codes.get(code, [])))})
    return rows


def batches(codes, size):
    for start in range(0, len(codes), size):
        yield codes[start : start + size]


def local_api_settings(path):
    settings = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            name, value = line.split("=", 1)
            settings[name.strip()] = value.strip().strip('"\'')
    return settings


def run_batch(codes, mapping_path, timeout_seconds):
    settings = local_api_settings(ROOT / ".env")
    missing = [name for name in REQUIRED_ENV if not (os.environ.get(name) or settings.get(name))]
    if missing:
        raise ValueError("필수 API 설정이 없습니다: " + ", ".join(missing))

    command = [
        str(ROOT / "gradlew"),
        "bootRun",
        "--args=--spring.profiles.active=local "
        "--spring.main.web-application-type=none "
        "--app.complex-import.mapping-path=" + str(mapping_path) + " "
        "--app.complex-import.legal-dong-codes=" + ",".join(codes),
    ]
    env = os.environ.copy()
    env.update({name: settings[name] for name in REQUIRED_ENV if name not in env})
    env["COMPLEX_IMPORT_ENABLED"] = "true"
    env["COMPLEX_IMPORT_SCHEDULE_ENABLED"] = "false"
    env["DATA_GO_KR_APT_LIST_ENABLED"] = "true"
    env["DATA_GO_KR_APT_BASIC_INFO_ENABLED"] = "true"
    env["LOCATION_IMPORT_ENABLED"] = "false"
    env["APARTMENT_TRADE_IMPORT_ENABLED"] = "false"
    # Gradle and Spring output is captured because it can contain API URLs/keys.
    try:
        result = subprocess.run(command, cwd=ROOT, env=env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                timeout=timeout_seconds, check=False)
    except subprocess.TimeoutExpired as exc:
        output = exc.stdout or b""
        if isinstance(output, bytes):
            output = output.decode("utf-8", errors="replace")
        return 124, output
    return result.returncode, result.stdout


def append_rows(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as target:
        for row in rows:
            row["recorded_at"] = datetime.now(timezone.utc).isoformat()
            target.write(json.dumps(row, ensure_ascii=False) + "\n")
        target.flush()
        os.fsync(target.fileno())


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", type=Path, default=DEFAULT_MAPPING)
    parser.add_argument("--known-source-gaps", type=Path, default=DEFAULT_SOURCE_GAPS)
    parser.add_argument("--manifest", type=Path, help="재개용 JSONL 파일 경로")
    parser.add_argument("--batch-size", type=int, default=5)
    parser.add_argument("--timeout-seconds", type=int, default=600)
    parser.add_argument("--start-index", type=int, default=0, help="0부터 시작하는 매핑 순서")
    parser.add_argument("--end-index", type=int, help="0부터 시작하는 종료 위치(미포함)")
    parser.add_argument("--max-consecutive-list-failures", type=int, default=3)
    parser.add_argument("--max-consecutive-api-failures", type=int, default=3)
    parser.add_argument("--list", action="store_true", help="코드와 이전 상태만 출력")
    parser.add_argument("--dry-run", action="store_true", help="실행 예정 배치만 출력")
    parser.add_argument("--run", action="store_true", help="실제 수동 적재 실행")
    parser.add_argument("--recheck-source-gaps", action="store_true",
                        help="원본 결측으로 분류된 법정동을 다시 조회")
    args = parser.parse_args(argv)
    if args.batch_size < 1 or args.batch_size > 20:
        parser.error("--batch-size는 1~20이어야 합니다")
    if args.timeout_seconds < 1:
        parser.error("--timeout-seconds는 1 이상이어야 합니다")
    if args.max_consecutive_list_failures < 1:
        parser.error("--max-consecutive-list-failures는 1 이상이어야 합니다")
    if args.max_consecutive_api_failures < 1:
        parser.error("--max-consecutive-api-failures는 1 이상이어야 합니다")
    if sum((args.list, args.dry_run, args.run)) != 1:
        parser.error("--list, --dry-run, --run 중 하나를 지정하세요")
    if args.run and args.manifest is None:
        parser.error("--run에는 --manifest가 필요합니다")

    try:
        codes = mapping_codes(args.mapping)
        source_gaps = known_source_gaps(args.known_source_gaps, set(codes))
        latest = latest_manifest(args.manifest, set(codes)) if args.manifest else {}
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.error(str(exc))
    end_index = args.end_index if args.end_index is not None else len(codes)
    if not 0 <= args.start_index < end_index <= len(codes):
        parser.error("매핑 인덱스 범위가 잘못됐습니다")

    selected = codes[args.start_index:end_index]
    pending = (
        [code for code in selected if code not in latest]
        + [code for code in selected
           if code in latest and (
               latest[code]["status"] not in TERMINAL
               or (args.recheck_source_gaps and latest[code]["status"] == SOURCE_GAP)
           )]
    )
    if args.list:
        for code in codes:
            print(f"{code}\t{latest.get(code, {}).get('status', 'PENDING')}")
        print(f"전체={len(codes)} 선택={len(selected)} 대기={len(pending)}")
        return 0

    if args.dry_run:
        for index, batch in enumerate(batches(pending, args.batch_size), 1):
            print(f"배치 {index}: {','.join(batch)}")
        print(f"전체={len(codes)} 선택={len(selected)} 대기={len(pending)}")
        return 0

    consecutive_list_failures = 0
    consecutive_api_failures = 0
    for index, batch in enumerate(batches(pending, args.batch_size), 1):
        print(f"배치 {index}: {len(batch)}개 실행", flush=True)
        try:
            exit_code, output = run_batch(batch, args.mapping, args.timeout_seconds)
            rows = parse_results(output, batch, exit_code, source_gaps)
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
        except OSError:
            exit_code = -1
            rows = parse_results("", batch, -1, source_gaps)
        append_rows(args.manifest, rows)
        for row in rows:
            print(f"{row['legal_dong_code']}\t{row['status']}\t{row['reason'] or ''}", flush=True)
            if row["status"] == "LIST_FAILED":
                consecutive_list_failures += 1
            else:
                consecutive_list_failures = 0
            if (row["status"] == "LIST_FAILED"
                or (row["status"] == "PARTIAL" and row["failed"] > 0
                    and row["created"] == 0 and row["skipped"] == 0)):
                consecutive_api_failures += 1
            else:
                consecutive_api_failures = 0
        if exit_code != 0 or any(row["status"] == "PROCESS_FAILED" for row in rows):
            if QUOTA_ERROR.search(output):
                print("API 일일 호출 한도 오류(22)로 중단했습니다.", file=sys.stderr)
                return 3
            print("프로세스 실패로 중단했습니다. 원인 확인 후 같은 명령으로 재개하세요.", file=sys.stderr)
            return 1
        if consecutive_list_failures >= args.max_consecutive_list_failures:
            print("목록 API 연속 실패 기준에 도달해 호출을 중단했습니다.", file=sys.stderr)
            return 1
        if consecutive_api_failures >= args.max_consecutive_api_failures:
            print("목록/기본정보 API 연속 실패 기준에 도달해 호출을 중단했습니다.", file=sys.stderr)
            return 1
    latest = latest_manifest(args.manifest, set(codes))
    return 0 if all(latest.get(code, {}).get("status") in TERMINAL for code in selected) else 1


if __name__ == "__main__":
    sys.exit(main())
