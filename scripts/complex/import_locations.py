#!/usr/bin/env python3
"""Run the local geocoding batch in bounded, resumable chunks."""

import argparse
from collections import Counter
import fcntl
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "data/complex/location-runs/local.jsonl"
REQUIRED_ENV = ("NAVER_MAPS_CLIENT_ID", "NAVER_MAPS_CLIENT_SECRET")


def local_settings(path=None):
    settings = {}
    path = path or ROOT / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if "=" not in line or line.lstrip().startswith("#"):
                continue
            name, value = line.split("=", 1)
            settings[name.strip()] = value.strip().strip('"\'')
    return settings


def entries_after(path, offset):
    with path.open("rb") as source:
        source.seek(offset)
        lines = source.read().decode("utf-8").splitlines()
    return [json.loads(line) for line in lines if line.strip()]


def latest_cursor(path):
    if not path.exists():
        return ""
    cursor = ""
    for entry in entries_after(path, 0):
        cursor = entry["nextAfterId"]
    return cursor


def run_batch(cursor, batch_size, manifest, env, timeout):
    before = manifest.stat().st_size if manifest.exists() else 0
    batch_env = env.copy()
    batch_env.update({
        "NAVER_GEOCODING_ENABLED": "true",
        "COMPLEX_LOCATION_IMPORT_ENABLED": "false",
        "COMPLEX_LOCATION_BATCH_ENABLED": "true",
        "COMPLEX_LOCATION_BATCH_AFTER_ID": cursor,
        "COMPLEX_LOCATION_BATCH_MAX_COUNT": str(batch_size),
        "COMPLEX_LOCATION_BATCH_MANIFEST_PATH": str(manifest),
        "GEOCODING_PROBE_ENABLED": "false",
        "COMPLEX_IMPORT_ENABLED": "false",
        "COMPLEX_IMPORT_SCHEDULE_ENABLED": "false",
        "LOCATION_IMPORT_ENABLED": "false",
    })
    # The application log can include request details; capture it without printing.
    process = subprocess.Popen(
        [str(ROOT / "gradlew"), "bootRun",
         "--args=--spring.profiles.active=local --spring.main.web-application-type=none"],
        cwd=ROOT,
        env=batch_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        start_new_session=True,
    )
    try:
        process.communicate(timeout=timeout)
        exit_code = process.returncode
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
        exit_code = 124
    return exit_code, entries_after(manifest, before) if manifest.exists() else []


def import_all(args):
    settings = local_settings(args.env_file)
    env = os.environ.copy()
    for name in REQUIRED_ENV:
        if not env.get(name) and settings.get(name):
            env[name] = settings[name]
    missing = [name for name in REQUIRED_ENV if not env.get(name)]
    if missing:
        raise ValueError("필수 환경 변수가 없습니다: " + ", ".join(missing))

    manifest = args.manifest.resolve()
    manifest.parent.mkdir(parents=True, exist_ok=True)
    lock_path = Path(str(manifest) + ".lock")
    with lock_path.open("a+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ValueError("같은 매니페스트의 적재가 이미 실행 중입니다") from None

        cursor = "" if args.start_over else latest_cursor(manifest)
        totals = Counter()
        for number in range(1, args.max_batches + 1):
            started = time.monotonic()
            exit_code, entries = run_batch(
                cursor, args.batch_size, manifest, env, args.timeout_seconds
            )
            totals.update(entry["resultKind"] for entry in entries)
            if entries:
                cursor = entries[-1]["nextAfterId"]
            print(
                f"구간 {number}: 시도 {len(entries)}건, "
                f"다음 커서 {cursor or '(처음)'}, "
                f"소요 {time.monotonic() - started:.1f}초, "
                f"이번 실행 결과 {dict(totals)}",
                flush=True,
            )
            if exit_code != 0 or any(
                entry["resultKind"] == "ABORTED" for entry in entries
            ):
                print(
                    "배치가 중단됐습니다. 마지막 기록 커서에서 다시 실행하세요.",
                    file=sys.stderr,
                )
                return 1
            if not entries:
                print("현재 커서 이후 미처리 단지 조회를 마쳤습니다.")
                return 0
        print("최대 구간 수에 도달했습니다. 같은 매니페스트로 재개하세요.")
        return 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true", help="실제 외부 API 호출과 DB 적재")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--batch-size", type=int, default=20)
    parser.add_argument("--max-batches", type=int, default=250)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--start-over", action="store_true")
    parser.add_argument("--env-file", type=Path)
    args = parser.parse_args(argv)
    if not 1 <= args.batch_size <= 20:
        parser.error("--batch-size는 1~20이어야 합니다")
    if args.max_batches < 1 or args.timeout_seconds < 1:
        parser.error("최대 구간 수와 제한 시간은 1 이상이어야 합니다")
    if not args.run:
        print("실제 적재에는 --run을 지정하세요.")
        return 0
    try:
        return import_all(args)
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        print(f"적재를 시작하거나 재개할 수 없습니다: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
