#!/usr/bin/env python3
"""Fill existing complex.sale_type using the basic-info API, with a resumable cursor."""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

from import_complexes import local_api_settings


ROOT = Path(__file__).resolve().parents[2]
RESULT = re.compile(r"단지 분양형태 적재 결과: (.*)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--after-id", default="")
    parser.add_argument("--max-count", type=int, default=100)
    parser.add_argument("--only-unpriced", action="store_true")
    parser.add_argument("--request-interval", default="300ms")
    parser.add_argument("--timeout", type=int, default=3600)
    args = parser.parse_args()
    if args.after_id and not re.fullmatch(r"[A-Za-z0-9]{1,20}", args.after_id):
        parser.error("--after-id must be a complex ID")
    if not 1 <= args.max_count <= 5000:
        parser.error("--max-count must be between 1 and 5000")

    settings = local_api_settings(ROOT / ".env")
    required = ("DATA_GO_KR_SERVICE_KEY", "DATA_GO_KR_APT_BASIC_INFO_BASE_URL")
    env = os.environ.copy()
    for name in required:
        if not env.get(name):
            env[name] = settings.get(name, "")
    if any(not env[name] for name in required):
        parser.error("basic-info API settings are missing")
    env["DATA_GO_KR_APT_BASIC_INFO_ENABLED"] = "true"
    env["COMPLEX_SALE_TYPE_BACKFILL_ENABLED"] = "true"
    env["COMPLEX_SALE_TYPE_BACKFILL_AFTER_ID"] = args.after_id
    env["COMPLEX_SALE_TYPE_BACKFILL_MAX_COUNT"] = str(args.max_count)
    env["COMPLEX_SALE_TYPE_BACKFILL_ONLY_UNPRICED"] = str(
        args.only_unpriced).lower()
    if not re.fullmatch(r"[1-9][0-9]*(?:ms|s)", args.request_interval):
        parser.error("--request-interval must be a positive duration such as 300ms")
    env["COMPLEX_IMPORT_REQUEST_INTERVAL"] = args.request_interval

    command = [str(ROOT / "gradlew"), "bootRun", "--quiet",
               "--args=--spring.profiles.active=local "
               "--spring.main.web-application-type=none"]
    try:
        completed = subprocess.run(command, cwd=ROOT, env=env, text=True,
                                   stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT,
                                   timeout=args.timeout, check=False)
    except subprocess.TimeoutExpired:
        print("Backfill timed out. Check DB and resume with --after-id.", file=sys.stderr)
        return 124

    match = RESULT.search(completed.stdout)
    if match:
        print(match.group(0))
    else:
        print("Backfill did not report a result. See local application logs.", file=sys.stderr)
    if completed.returncode:
        print(f"Application exited with code {completed.returncode}.", file=sys.stderr)
    return completed.returncode


if __name__ == "__main__":
    sys.exit(main())
