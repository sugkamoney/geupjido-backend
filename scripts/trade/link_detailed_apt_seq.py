#!/usr/bin/env python3
"""검증된 상세 API aptSeq → K-apt 단지 대응을 다른 거래에 재사용한다.

기본 실행은 감사만 수행한다. --apply는 현재 로컬 DB의 미연결 거래만 갱신한다.
"""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

from link_detailed_road_address import (
    ROOT, apply_plan, fetch_detail, read_db, read_service_key,
    trade_key_from_api, trade_key_from_db,
)


def load_db(container):
    trades = read_db(container,
        "SELECT id, complex_id, zone_id, deal_date, deal_amount, "
        "exclusive_area, floor, build_year, district_code, "
        "legal_dong_name, raw_apartment_name, lot_number, is_canceled "
        "FROM apartment_trade")
    complexes = read_db(container,
        "SELECT id, name, approval_date, legal_dong_code, zone_id, "
        "sale_type FROM complex")
    by_key = defaultdict(list)
    for row in trades:
        by_key[trade_key_from_db(row)].append(row)
    return by_key, {row["id"]: row for row in complexes}


def load_months(from_month="202510", through_month="202610"):
    mapping = json.loads((ROOT / "data/location/mapping/location-mapping.json")
                         .read_text(encoding="utf-8"))
    districts = sorted({city["id"] for city in mapping["cities"]})
    year, month = int(from_month[:4]), int(from_month[4:])
    months = []
    while f"{year:04}{month:02}" <= through_month:
        months.append(f"{year:04}{month:02}")
        year, month = (year + 1, 1) if month == 12 else (year, month + 1)
    return [(district, month) for district in districts for month in months]


def load_cached_detail(service_key, district, month, cache_dir):
    if cache_dir is None:
        return fetch_detail(service_key, district, month)
    path = cache_dir / f"{district}-{month}.xml"
    if path.exists():
        root = ET.parse(path).getroot()
        if root.tag != "items":
            raise ValueError(f"상세 API 캐시 형식이 올바르지 않습니다: {path}")
        return root.findall("item")
    items = fetch_detail(service_key, district, month)
    cache_dir.mkdir(parents=True, exist_ok=True)
    root = ET.Element("items")
    root.extend(items)
    temporary = path.with_suffix(".tmp")
    ET.ElementTree(root).write(temporary, encoding="utf-8",
                               xml_declaration=True)
    temporary.replace(path)
    return items


def collect_plan(service_key, db_by_key, complex_by_id,
                 cache_dir=None, requests=None):
    mapping = defaultdict(set)
    unlinked = defaultdict(list)
    stats = Counter()
    requests = load_months() if requests is None else requests
    for index, (district, month) in enumerate(requests, 1):
        for item in load_cached_detail(service_key, district, month, cache_dir):
            apt_seq = (item.findtext("aptSeq") or "").strip()
            if not apt_seq:
                stats["missing_apt_seq"] += 1
                continue
            key = trade_key_from_api(item)
            rows = db_by_key.get(key, ())
            if not rows:
                stats["no_exact_db_key"] += 1
                continue
            if len(rows) != 1:
                stats["ambiguous_db_key"] += 1
                continue
            row = rows[0]
            if row["complex_id"]:
                mapping[apt_seq].add((row["complex_id"], row["zone_id"]))
            else:
                unlinked[row["id"]].append((apt_seq, row,
                    (item.findtext("sggCd") or "")
                    + (item.findtext("umdCd") or "")))
        if index % 50 == 0:
            print(f"상세 API 대조: {index}/{len(requests)} 시군구·월",
                  flush=True)

    plan = []
    for trade_id, matches in unlinked.items():
        if len(matches) != 1:
            stats["duplicate_api_key"] += 1
            continue
        apt_seq, row, legal_code = matches[0]
        options = mapping.get(apt_seq, ())
        if not options:
            stats["no_verified_apt_seq_bridge"] += 1
            continue
        if len(options) != 1:
            stats["ambiguous_apt_seq_bridge"] += 1
            continue
        complex_id, zone_id = next(iter(options))
        candidate = complex_by_id.get(complex_id)
        if candidate is None or candidate["zone_id"] != zone_id:
            stats["changed_complex"] += 1
            continue
        if (candidate["legal_dong_code"] != legal_code
                or row["district_code"] != legal_code[:5]):
            stats["legal_dong_mismatch"] += 1
            continue
        sale_type = candidate["sale_type"] or ""
        if "임대" in sale_type and "분양" not in sale_type and "혼합" not in sale_type:
            stats["rental_only"] += 1
            continue
        plan.append((int(trade_id), apt_seq, complex_id, zone_id))
    stats["verified_apt_seq_count"] = len(mapping)
    stats["ambiguous_existing_bridge_count"] = sum(
        len(values) > 1 for values in mapping.values()
    )
    return sorted(plan), stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--container", default="geupjido-postgres")
    parser.add_argument("--cache-dir", type=Path,
                        default=ROOT / "data/trade/import-runs/detail-apt-seq-cache")
    parser.add_argument("--from-month", default="202510")
    parser.add_argument("--through-month", default="202610")
    args = parser.parse_args()
    for month in (args.from_month, args.through_month):
        if not re.fullmatch(r"\d{4}(0[1-9]|1[0-2])", month):
            parser.error("months must use yyyyMM")
    if args.from_month > args.through_month:
        parser.error("from-month must not follow through-month")
    db_by_key, complex_by_id = load_db(args.container)
    plan, stats = collect_plan(read_service_key(ROOT / ".env"),
                               db_by_key, complex_by_id, args.cache_dir,
                               load_months(args.from_month,
                                           args.through_month))
    print("검증 통계:", dict(stats))
    print("추가 연결 가능:", len(plan), "건")
    if args.apply and plan:
        apply_plan(args.container, plan)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as error:
        print("실패:", error, file=sys.stderr)
        sys.exit(1)
