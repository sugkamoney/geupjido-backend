#!/usr/bin/env python3
"""Run the full weekly trade refresh once against the local database."""

import argparse
import os
from pathlib import Path
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/complex"))
from import_complexes import local_api_settings  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--months-to-collect", type=int, default=3)
    parser.add_argument("--timeout", type=int, default=7200)
    args = parser.parse_args()
    if args.months_to_collect < 1 or args.timeout < 1:
        parser.error("months-to-collect and timeout must be positive")

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
        "WEEKLY_TRADE_REFRESH_RUN_ON_STARTUP": "true",
        "WEEKLY_TRADE_REFRESH_SCHEDULE_ENABLED": "false",
        "WEEKLY_TRADE_REFRESH_SMOKE_RUN_ON_STARTUP": "false",
        "WEEKLY_TRADE_REFRESH_MONTHS_TO_COLLECT": str(args.months_to_collect),
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
        print("전체 실행 시간이 초과되었습니다. 결과 TSV와 DB를 확인하세요.",
              file=sys.stderr)
        return 124
    for line in completed.stdout.splitlines():
        if ("실거래가 주간 갱신 시작" in line
                or "실거래가 주간 갱신 미완료" in line
                or "실거래가 주간 갱신 완료" in line
                or "실거래가 주간 수집 실패" in line):
            print(line)
    if completed.returncode:
        print(f"전체 실행이 종료 코드 {completed.returncode}으로 끝났습니다. "
              "결과 TSV와 DB를 확인하세요.", file=sys.stderr)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
