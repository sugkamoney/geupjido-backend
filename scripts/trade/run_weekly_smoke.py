#!/usr/bin/env python3
"""Run one district/month through the weekly trade refresh pipeline."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/complex"))
from import_complexes import local_api_settings  # noqa: E402


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

    settings = local_api_settings(ROOT / ".env")
    env = os.environ.copy()
    if not env.get("DATA_GO_KR_SERVICE_KEY"):
        env["DATA_GO_KR_SERVICE_KEY"] = settings.get("DATA_GO_KR_SERVICE_KEY", "")
    if not env["DATA_GO_KR_SERVICE_KEY"]:
        parser.error("DATA_GO_KR_SERVICE_KEY is missing")
    env.update({
        "DATA_GO_KR_APT_TRADE_ENABLED": "true",
        "DATA_GO_KR_APT_TRADE_BASE_URL": env.get("DATA_GO_KR_APT_TRADE_BASE_URL")
            or "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade",
        "WEEKLY_TRADE_REFRESH_RUN_ON_STARTUP": "false",
        "WEEKLY_TRADE_REFRESH_SCHEDULE_ENABLED": "false",
        "WEEKLY_TRADE_REFRESH_SMOKE_RUN_ON_STARTUP": "true",
        "WEEKLY_TRADE_REFRESH_SMOKE_DISTRICT_CODE": args.district_code,
        "WEEKLY_TRADE_REFRESH_SMOKE_DEAL_YEAR_MONTH": args.deal_year_month,
        "APARTMENT_TRADE_IMPORT_ENABLED": "false",
        "APARTMENT_TRADE_BACKFILL_ENABLED": "false",
        "APARTMENT_TRADE_MATCH_ENABLED": "false",
        "COMPLEX_PRICE_RECALCULATION_ENABLED": "false",
    })
    command = [str(ROOT / "gradlew"), "bootRun", "--quiet",
               "--args=--spring.profiles.active=local "
               "--spring.main.web-application-type=none"]
    try:
        completed = subprocess.run(
            command, cwd=ROOT, env=env, text=True,
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            timeout=args.timeout, check=False,
        )
    except subprocess.TimeoutExpired:
        print("시험 실행 시간이 초과되었습니다. 결과 TSV와 DB를 확인하세요.",
              file=sys.stderr)
        return 124
    for line in completed.stdout.splitlines():
        if ("실거래가 주간 갱신 시험 실행" in line
                or "실거래가 주간 수집 결과" in line
                or "실거래가 주간 수집 실패" in line):
            print(line)
    if completed.returncode:
        print(f"시험 실행이 종료 코드 {completed.returncode}으로 끝났습니다. "
              "결과 TSV와 DB를 확인하세요.", file=sys.stderr)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
