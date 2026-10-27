#!/usr/bin/env python3
"""Resume the local complex importer after temporary API errors, with bounded pauses."""

import argparse
import fcntl
import json
from pathlib import Path
import sys
import time

import import_complexes


DEFAULT_MANIFEST = (
    import_complexes.ROOT / "data/complex/import-runs/local.jsonl"
)


def completed_count(manifest, codes):
    latest = import_complexes.latest_manifest(manifest, set(codes))
    return sum(latest.get(code, {}).get("status") in import_complexes.TERMINAL for code in codes)


def source_gap_count(manifest, codes):
    latest = import_complexes.latest_manifest(manifest, set(codes))
    return sum(latest.get(code, {}).get("status") == import_complexes.SOURCE_GAP
               for code in codes)


def recorded_created_count(manifest):
    if not manifest.exists():
        return 0
    with manifest.open(encoding="utf-8") as source:
        return sum(json.loads(line).get("created", 0) for line in source if line.strip())


def run_until_complete(
    manifest,
    codes,
    batch_size,
    cooldown_seconds,
    max_no_progress_rounds,
    max_rounds,
    import_main=import_complexes.main,
    sleeper=time.sleep,
):
    no_progress_rounds = 0
    for round_number in range(1, max_rounds + 1):
        before = completed_count(manifest, codes)
        before_created = recorded_created_count(manifest)
        if before == len(codes):
            print(
                f"전체 처리: {before}/{len(codes)}, "
                f"원본 결측 법정동 {source_gap_count(manifest, codes)}곳",
                flush=True,
            )
            return 0

        print(
            f"재개 {round_number}/{max_rounds}: 처리 {before}/{len(codes)}, "
            f"원본 결측 법정동 {source_gap_count(manifest, codes)}곳",
            flush=True,
        )
        result = import_main([
            "--run", "--manifest", str(manifest),
            "--batch-size", str(batch_size),
            "--max-consecutive-list-failures", "3",
        ])
        after = completed_count(manifest, codes)
        newly_created = recorded_created_count(manifest) - before_created
        print(
            f"재개 결과: 처리 {after}/{len(codes)}, "
            f"원본 결측 법정동 {source_gap_count(manifest, codes)}곳, "
            f"신규 단지 {newly_created}건",
            flush=True,
        )

        if after == len(codes):
            return 0
        if result == 3:
            print("호출 한도 소진으로 재개를 중단합니다.", file=sys.stderr)
            return 3
        if result != 1:
            print("적재 도구가 예상하지 못한 상태로 종료됐습니다.", file=sys.stderr)
            return 2

        no_progress_rounds = (
            0 if after > before or newly_created > 0 else no_progress_rounds + 1
        )
        if no_progress_rounds >= max_no_progress_rounds:
            print("여러 차례 진전이 없어 자동 재개를 중단합니다.", file=sys.stderr)
            return 1
        if round_number == max_rounds:
            break
        wait_seconds = min(
            cooldown_seconds * 2 ** max(0, no_progress_rounds - 1),
            900,
        )
        print(f"API 회복 대기: {wait_seconds}초", flush=True)
        sleeper(wait_seconds)

    print("최대 재개 횟수에 도달했습니다.", file=sys.stderr)
    return 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--batch-size", type=int, default=2)
    parser.add_argument("--cooldown-seconds", type=int, default=120)
    parser.add_argument("--max-no-progress-rounds", type=int, default=3)
    parser.add_argument("--max-rounds", type=int, default=100)
    args = parser.parse_args(argv)

    if not 1 <= args.batch_size <= 20:
        parser.error("--batch-size는 1~20이어야 합니다")
    if args.cooldown_seconds < 0:
        parser.error("--cooldown-seconds는 0 이상이어야 합니다")
    if args.max_no_progress_rounds < 1 or args.max_rounds < 1:
        parser.error("최대 재개 횟수는 1 이상이어야 합니다")
    try:
        codes = import_complexes.mapping_codes(import_complexes.DEFAULT_MAPPING)
        import_complexes.latest_manifest(args.manifest, set(codes))
    except (OSError, ValueError, KeyError, TypeError) as exc:
        parser.error(str(exc))

    lock_path = Path(str(args.manifest) + ".lock")
    try:
        lock_path.parent.mkdir(parents=True, exist_ok=True)
        lock_file = lock_path.open("a+")
    except OSError as exc:
        print(f"재개 작업 잠금 파일을 열 수 없습니다: {exc}", file=sys.stderr)
        return 2
    with lock_file:
        try:
            fcntl.flock(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            print("같은 매니페스트의 재개 작업이 이미 실행 중입니다.", file=sys.stderr)
            return 2
        try:
            return run_until_complete(
                args.manifest,
                codes,
                args.batch_size,
                args.cooldown_seconds,
                args.max_no_progress_rounds,
                args.max_rounds,
            )
        finally:
            fcntl.flock(lock_file, fcntl.LOCK_UN)


if __name__ == "__main__":
    sys.exit(main())
