#!/usr/bin/env python3
"""재시도 이력이 포함된 소급 수집 결과에서 조합별 최신 상태를 추린다."""

import argparse
import csv
from collections import Counter
from pathlib import Path


def write_tsv(path, columns, rows):
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t")
        writer.writerow(columns)
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output-dir", type=Path, default=Path("build/reports"))
    args = parser.parse_args()

    latest = {}
    with args.manifest.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source, delimiter="\t"):
            key = (row["계약월"], row["시군구코드"])
            latest[key] = row

    args.output_dir.mkdir(parents=True, exist_ok=True)
    write_tsv(
        args.output_dir / "trade-import-latest.tsv",
        ("계약월", "시군구코드", "상태", "실패사유", "원본", "신규",
         "건너뛰기", "갱신"),
        ((month, district, row["상태"], row.get("실패사유", ""),
          row["원본"], row["신규"], row["건너뛰기"], row["갱신"])
         for (month, district), row in sorted(latest.items())),
    )
    by_district = Counter(
        (district, row["상태"], row.get("실패사유", ""))
        for (_, district), row in latest.items()
    )
    write_tsv(
        args.output_dir / "trade-import-by-district.tsv",
        ("시군구코드", "상태", "실패사유", "계약월수"),
        ((*key, count) for key, count in sorted(by_district.items())),
    )
    print(f"시군구·월 {len(latest)}개 조합의 최신 상태를 기록했습니다: {args.output_dir}")


if __name__ == "__main__":
    main()
