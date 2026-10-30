#!/usr/bin/env python3
"""Run the Spring trade backfill with credentials from the local environment."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts/complex"))
from import_complexes import local_api_settings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from-month", required=True)
    parser.add_argument("--through-month", required=True)
    parser.add_argument("--max-pairs", type=int, default=40)
    parser.add_argument("--manifest", default="data/trade/import-runs/local.tsv")
    parser.add_argument("--timeout", type=int, default=3600)
    args = parser.parse_args()
    for month in (args.from_month, args.through_month):
        if not re.fullmatch(r"\d{4}(0[1-9]|1[0-2])", month):
            parser.error("months must use yyyyMM")
    if args.from_month > args.through_month or args.max_pairs < 1:
        parser.error("invalid month range or max-pairs")

    settings = local_api_settings(ROOT / ".env")
    env = os.environ.copy()
    for name in ("DATA_GO_KR_SERVICE_KEY", "DATA_GO_KR_APT_TRADE_BASE_URL"):
        if not env.get(name):
            env[name] = settings.get(name, "")
    if not env["DATA_GO_KR_APT_TRADE_BASE_URL"]:
        env["DATA_GO_KR_APT_TRADE_BASE_URL"] = (
            "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade"
        )
    if not env["DATA_GO_KR_SERVICE_KEY"]:
        parser.error("trade API settings are missing")
    env.update({
        "DATA_GO_KR_APT_TRADE_ENABLED": "true",
        "APARTMENT_TRADE_BACKFILL_ENABLED": "true",
        "APARTMENT_TRADE_BACKFILL_FROM_MONTH": args.from_month,
        "APARTMENT_TRADE_BACKFILL_THROUGH_MONTH": args.through_month,
        "APARTMENT_TRADE_BACKFILL_MAX_PAIRS": str(args.max_pairs),
        "APARTMENT_TRADE_BACKFILL_MANIFEST_PATH": args.manifest,
    })
    command = [str(ROOT / "gradlew"), "bootRun", "--quiet",
               "--args=--spring.profiles.active=local "
               "--spring.main.web-application-type=none"]
    try:
        completed = subprocess.run(command, cwd=ROOT, env=env, text=True,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   timeout=args.timeout, check=False)
    except subprocess.TimeoutExpired:
        print("Backfill timed out. Resume with the same manifest.", file=sys.stderr)
        return 124
    for line in completed.stdout.splitlines():
        if "실거래가 소급 적재 실행 완료." in line or "실거래가 소급 적재 실패." in line:
            print(line)
    if completed.returncode:
        print(f"Application exited with code {completed.returncode}.", file=sys.stderr)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
