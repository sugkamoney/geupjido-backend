#!/usr/bin/env python3
"""Clone the local PostGIS DB, run one trade pair, and discard the clone."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
DB_NAME = "geupjido_weekly_trade_smoke"
DB_SCRIPT = ROOT / "scripts/test/worldcup-browser-smoke-db.sh"
SMOKE_SCRIPT = ROOT / "scripts/trade/run_weekly_smoke.py"


def run(*args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def database_exists():
    result = run(
        "docker", "exec", "-u", "postgres", "geupjido-postgres",
        "psql", "-d", "postgres", "-Atc",
        f"SELECT 1 FROM pg_database WHERE datname = '{DB_NAME}'",
        capture_output=True, text=True,
    )
    return result.stdout.strip() == "1"


def snapshot(database_name, district_code, deal_year_month):
    # The two arguments are validated as digits before they reach this query.
    sql = (
        "SELECT "
        "(SELECT count(*) FROM apartment_trade WHERE district_code = "
        f"'{district_code}' AND to_char(deal_date, 'YYYYMM') = '{deal_year_month}'), "
        "(SELECT count(*) FROM apartment_trade WHERE district_code = "
        f"'{district_code}' AND to_char(deal_date, 'YYYYMM') = '{deal_year_month}' "
        "AND complex_id IS NOT NULL), "
        "(SELECT count(*) FROM complex WHERE price IS NOT NULL), "
        "(SELECT coalesce(sum(price), 0) FROM complex WHERE price IS NOT NULL)"
    )
    result = run(
        "docker", "exec", "-u", "postgres", "geupjido-postgres",
        "psql", "-d", database_name, "-Atc", sql,
        capture_output=True, text=True,
    )
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--district-code", required=True)
    parser.add_argument("--deal-year-month", required=True)
    parser.add_argument("--timeout", type=int, default=900)
    args = parser.parse_args()
    if not re.fullmatch(r"\d{5}", args.district_code):
        parser.error("district-code must be five digits")
    if not re.fullmatch(r"\d{4}(0[1-9]|1[0-2])", args.deal_year_month):
        parser.error("deal-year-month must use yyyyMM")
    if args.timeout < 1:
        parser.error("timeout must be positive")
    if database_exists():
        parser.error(f"검증 DB가 이미 있습니다: {DB_NAME}")

    # Construct a small environment so an inherited Spring datasource setting
    # cannot redirect the runner away from the cloned local database.
    env = {name: os.environ[name] for name in
           ("PATH", "JAVA_HOME", "HOME", "TMPDIR", "LANG", "LC_ALL", "TZ")
           if name in os.environ}
    env.update({
        "DB_URL": f"jdbc:postgresql://127.0.0.1:5433/{DB_NAME}",
        "DB_USERNAME": "geupjido",
        "DB_PASSWORD": "geupjido-local",
    })
    if "DATA_GO_KR_SERVICE_KEY" in os.environ:
        env["DATA_GO_KR_SERVICE_KEY"] = os.environ["DATA_GO_KR_SERVICE_KEY"]

    source_before = snapshot("geupjido", args.district_code, args.deal_year_month)
    clone_attempted = False
    try:
        clone_attempted = True
        run("bash", str(DB_SCRIPT), "create", DB_NAME)
        before = snapshot(DB_NAME, args.district_code, args.deal_year_month)
        if before != source_before:
            raise RuntimeError("검증 DB의 초기 집계가 원본 DB와 다릅니다.")
        print(f"검증 전: 거래|연결 거래|가격 단지|가격 합계 = {before}", flush=True)

        with tempfile.TemporaryDirectory(prefix="geupjido-weekly-trade-") as folder:
            env["WEEKLY_TRADE_REFRESH_REPORT_PATH"] = str(
                Path(folder) / "weekly-smoke.tsv"
            )
            result = subprocess.run(
                [sys.executable, "-B", str(SMOKE_SCRIPT),
                 "--district-code", args.district_code,
                 "--deal-year-month", args.deal_year_month,
                 "--timeout", str(args.timeout)],
                cwd=ROOT, env=env, check=False,
            )
            after = snapshot(DB_NAME, args.district_code, args.deal_year_month)
            print(f"검증 후: 거래|연결 거래|가격 단지|가격 합계 = {after}", flush=True)
            source_after = snapshot("geupjido", args.district_code, args.deal_year_month)
            if source_after != source_before:
                raise RuntimeError("원본 로컬 DB 집계가 검증 중 바뀌었습니다.")
            print("원본 로컬 DB 집계 유지 확인", flush=True)
            return result.returncode
    finally:
        # Also clean up a database left by a failed pg_restore during creation.
        if clone_attempted:
            run("bash", str(DB_SCRIPT), "drop", DB_NAME)


if __name__ == "__main__":
    sys.exit(main())
