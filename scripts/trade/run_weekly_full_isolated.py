#!/usr/bin/env python3
"""Run all weekly trade targets against a disposable local PostGIS clone."""

import argparse
from collections import Counter
import csv
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
from zoneinfo import ZoneInfo


ROOT = Path(__file__).resolve().parents[2]
DB_NAME = "geupjido_weekly_trade_full_smoke"
DB_SCRIPT = ROOT / "scripts/test/worldcup-browser-smoke-db.sh"
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
REPORT_DIR = ROOT / "data/trade/import-runs"
REPORT_HEADER = ["실행시각", "시군구코드", "계약월", "상태", "원본", "신규",
                 "건너뛰기", "갱신", "충돌", "실패사유", "신규충돌"]


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def query(database, sql):
    result = run("docker", "exec", "-u", "postgres", "geupjido-postgres",
                 "psql", "-d", database, "-Atc", sql,
                 capture_output=True, text=True)
    return result.stdout.strip()


def snapshot(database):
    return query(database, "SELECT "
                 "(SELECT count(*) FROM apartment_trade), "
                 "(SELECT count(*) FROM apartment_trade WHERE complex_id IS NOT NULL), "
                 "(SELECT count(*) FROM apartment_trade_conflict), "
                 "(SELECT count(*) FROM complex WHERE price IS NOT NULL), "
                 "(SELECT coalesce(sum(price), 0) FROM complex WHERE price IS NOT NULL)")


def expected_pairs(months):
    zones = json.loads(MAPPING.read_text(encoding="utf-8"))["zones"]
    districts = sorted({zone["cityId"] for zone in zones})
    now = datetime.now(ZoneInfo("Asia/Seoul"))
    year, month = now.year, now.month
    result = set()
    for offset in range(months):
        index = year * 12 + month - 1 - offset
        deal_month = f"{index // 12:04d}{index % 12 + 1:02d}"
        result.update((district, deal_month) for district in districts)
    return result


def read_report(path, expected, previous_count):
    with path.open(newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != REPORT_HEADER:
            raise RuntimeError("조합 결과 파일의 열 구성이 다릅니다.")
        rows = list(reader)
    current = rows[previous_count:]
    pairs = [(row["시군구코드"], row["계약월"]) for row in current]
    counts = Counter(row["상태"] for row in current)
    print(f"처리 조합: {len(current)}/{len(expected)}, 결과: {dict(sorted(counts.items()))}",
          flush=True)
    if len(current) != len(expected) or set(pairs) != expected or len(set(pairs)) != len(pairs):
        missing = sorted(expected - set(pairs))
        raise RuntimeError(f"대상 조합이 누락되거나 중복됐습니다: 첫 누락={missing[:3]}")
    problems = [row for row in current if row["상태"] != "SUCCESS"]
    for row in problems[:10]:
        print(f"검토: {row['시군구코드']} {row['계약월']} "
              f"{row['상태']} {row['실패사유']}", flush=True)
    if len(problems) > 10:
        print(f"추가 검토 조합 {len(problems) - 10}개는 결과 TSV를 확인하세요.",
              flush=True)
    return len(rows), counts, sum(int(row["신규"]) for row in current), \
        sum(int(row["신규충돌"]) for row in current)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--months-to-collect", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=7200)
    parser.add_argument("--plan-only", action="store_true")
    parser.add_argument("--verify-rerun", action="store_true")
    args = parser.parse_args()
    if args.months_to_collect < 1 or args.timeout < 1:
        parser.error("개월 수와 제한 시간은 양수여야 합니다.")
    expected = expected_pairs(args.months_to_collect)
    print(f"전체 대상: 시군구 {len({district for district, _ in expected})}곳, "
          f"최근 {args.months_to_collect}개월, 조합 {len(expected)}개", flush=True)
    if args.plan_only:
        return 0
    if query("postgres", f"SELECT 1 FROM pg_database WHERE datname = '{DB_NAME}'") == "1":
        parser.error(f"검증 DB가 이미 있습니다: {DB_NAME}")

    env = {name: os.environ[name] for name in
           ("PATH", "JAVA_HOME", "HOME", "TMPDIR", "LANG", "LC_ALL", "TZ")
           if name in os.environ}
    env.update({"DB_URL": f"jdbc:postgresql://127.0.0.1:5433/{DB_NAME}",
                "DB_USERNAME": "geupjido", "DB_PASSWORD": "geupjido-local",
                "WORLDCUP_RATING_SCHEDULE_ENABLED": "false",
                "WEEKLY_REPORT_SCHEDULE_ENABLED": "false"})
    if os.environ.get("DATA_GO_KR_SERVICE_KEY"):
        env["DATA_GO_KR_SERVICE_KEY"] = os.environ["DATA_GO_KR_SERVICE_KEY"]
    if os.environ.get("DATA_GO_KR_APT_TRADE_BASE_URL"):
        env["DATA_GO_KR_APT_TRADE_BASE_URL"] = os.environ["DATA_GO_KR_APT_TRADE_BASE_URL"]

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = REPORT_DIR / f"weekly-full-{datetime.now():%Y%m%dT%H%M%S}-{os.getpid()}.tsv"
    env["WEEKLY_TRADE_REFRESH_REPORT_PATH"] = str(report)
    source_before = snapshot("geupjido")
    attempted = False
    try:
        attempted = True
        run("bash", str(DB_SCRIPT), "create", DB_NAME)
        if snapshot(DB_NAME) != source_before:
            raise RuntimeError("복제 DB의 초기 집계가 원본과 다릅니다.")
        previous_count = 0
        before = source_before
        for attempt in range(2 if args.verify_rerun else 1):
            print(f"전체 갱신 {attempt + 1}회차 시작", flush=True)
            log = report.with_name(report.stem + f"-run{attempt + 1}.log")
            result = subprocess.run(
                [sys.executable, "-B", str(ROOT / "scripts/trade/run_weekly_refresh.py"),
                 "--months-to-collect", str(args.months_to_collect),
                 "--timeout", str(args.timeout), "--log-file", str(log)],
                cwd=ROOT, env=env, check=False,
            )
            print(f"실행 로그: {log}", flush=True)
            if not report.exists():
                raise RuntimeError("조합 결과 파일이 생성되지 않았습니다.")
            previous_count, counts, created, new_conflicts = read_report(
                report, expected, previous_count)
            after = snapshot(DB_NAME)
            print(f"DB 집계 전후(거래|연결|충돌|가격 단지|가격 합계): {before} → {after}", flush=True)
            print(f"신규 거래={created}, 신규 충돌={new_conflicts}", flush=True)
            if result.returncode or counts["FAILED"]:
                raise RuntimeError(f"전체 갱신이 실패했습니다: 종료 코드 {result.returncode}")
            if attempt and (before != after or created or new_conflicts):
                raise RuntimeError("재실행 후 신규 거래·충돌 또는 DB 집계 변경이 있습니다.")
            before = after
        return 0
    finally:
        if attempted:
            run("bash", str(DB_SCRIPT), "drop", DB_NAME)
        source_after = snapshot("geupjido")
        if source_before != source_after:
            raise RuntimeError(f"원본 DB 집계가 변경됐습니다: {source_before} → {source_after}")
        print(f"원본 DB 집계 유지 확인. 결과 파일: {report}", flush=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
