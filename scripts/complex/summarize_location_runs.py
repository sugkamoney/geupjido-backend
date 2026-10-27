#!/usr/bin/env python3
"""Summarize the latest attempt for each complex in a local location manifest."""

import argparse
from collections import Counter
import json
from pathlib import Path


DEFAULT_MANIFEST = Path("data/complex/location-runs/local.jsonl")


def summarize(path):
    latest = {}
    attempts = 0
    with path.open(encoding="utf-8") as source:
        for number, line in enumerate(source, start=1):
            if not line.strip():
                continue
            try:
                entry = json.loads(line)
                complex_id = entry["complexId"]
                result_kind = entry["resultKind"]
            except (ValueError, KeyError, TypeError) as exc:
                raise ValueError(f"{path}:{number}: 결과 행이 올바르지 않습니다") from exc
            if not isinstance(complex_id, str) or not isinstance(result_kind, str):
                raise ValueError(f"{path}:{number}: 단지 ID 또는 결과 종류가 올바르지 않습니다")
            latest[complex_id] = entry
            attempts += 1
    kinds = Counter(entry["resultKind"] for entry in latest.values())
    statuses = Counter(
        entry.get("zoneStatus") or "NONE" for entry in latest.values()
    )
    return attempts, latest, kinds, statuses


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--show-unresolved", action="store_true")
    args = parser.parse_args()
    try:
        attempts, latest, kinds, statuses = summarize(args.manifest)
    except (OSError, ValueError) as exc:
        parser.error(str(exc))

    print(f"시도 {attempts}건, 고유 단지 {len(latest)}개")
    print("처리 결과:", dict(sorted(kinds.items())))
    print("권역 판정:", dict(sorted(statuses.items())))
    if args.show_unresolved:
        for complex_id, entry in sorted(latest.items()):
            if entry.get("zoneStatus") != "MATCHED":
                print(
                    f"{complex_id}\t{entry['resultKind']}\t"
                    f"{entry.get('zoneStatus') or 'NONE'}"
                )


if __name__ == "__main__":
    main()
