#!/usr/bin/env python3
"""상세 거래 주소와 단독 사용 도로명주소가 일치하는 단지를 연결한다.

로컬 초기 적재를 위한 수동 도구다. 기본 실행은 감사만 하고 --apply 때만
거래의 complex_id와 zone_id를 같은 트랜잭션에서 갱신한다.
"""

import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from link_detailed_road_address import (
    ROOT, address_matches, apply_plan, read_db,
    read_service_key, trade_key_from_api, trade_key_from_db,
)
from link_detailed_apt_seq import load_cached_detail


def load_db(container, from_month="202510", through_month="202610"):
    from_date = f"{from_month[:4]}-{from_month[4:]}-01"
    end_year, end_month = int(through_month[:4]), int(through_month[4:])
    end_year, end_month = ((end_year + 1, 1) if end_month == 12
                           else (end_year, end_month + 1))
    until_date = f"{end_year:04}-{end_month:02}-01"
    trades = read_db(container,
        "SELECT id, complex_id, zone_id, deal_date, deal_amount, "
        "exclusive_area, floor, build_year, district_code, "
        "legal_dong_name, raw_apartment_name, lot_number, is_canceled "
        f"FROM apartment_trade WHERE deal_date >= '{from_date}' "
        f"AND deal_date < '{until_date}'")
    complexes = read_db(container,
        "SELECT id, name, address_road, approval_date, legal_dong_code, "
        "zone_id, sale_type FROM complex")
    by_key = defaultdict(list)
    for row in trades:
        by_key[trade_key_from_db(row)].append(row)
    by_legal_code = defaultdict(list)
    for row in complexes:
        if row["legal_dong_code"] and row["zone_id"] and row["address_road"]:
            by_legal_code[row["legal_dong_code"]].append(row)
    return by_key, by_legal_code


def candidate_for(item, row, by_legal_code):
    legal_code = ((item.findtext("sggCd") or "")
                  + (item.findtext("umdCd") or ""))
    if len(legal_code) != 10 or row["district_code"] != legal_code[:5]:
        return None, "LEGAL_CODE_MISMATCH"
    if (item.findtext("umdNm") or "").strip() != row["legal_dong_name"]:
        return None, "LEGAL_DONG_NAME_MISMATCH"
    road_matches = [candidate for candidate in by_legal_code[legal_code]
                    if address_matches(item, candidate["address_road"])]
    if not road_matches:
        return None, "NO_ROAD_CANDIDATE"
    # 같은 주소를 임대·분양 단지가 공유하면 도로명만으로 구분할 수 없다.
    if len(road_matches) != 1:
        return None, "SHARED_ROAD_ADDRESS"
    candidate = road_matches[0]
    sale_type = candidate["sale_type"] or ""
    if "임대" in sale_type and "분양" not in sale_type and "혼합" not in sale_type:
        return None, "RENTAL_ONLY"
    trade_name = "".join((row.get("raw_apartment_name") or "").split())
    candidate_name = "".join((candidate.get("name") or "").split())
    if not trade_name or trade_name != candidate_name:
        return None, "NAME_REVIEW"
    return candidate, "MATCHED"


def decide_plan(observations, existing_links, stats):
    proposed_links = defaultdict(set)
    for matches in observations.values():
        for apt_seq, candidate in matches:
            proposed_links[apt_seq].add(candidate["id"])
    plan = []
    for trade_id, matches in observations.items():
        if len(matches) != 1:
            stats["DUPLICATE_API_KEY"] += 1
            continue
        apt_seq, candidate = matches[0]
        if proposed_links[apt_seq] != {candidate["id"]}:
            stats["APT_SEQ_CANDIDATE_CONFLICT"] += 1
            continue
        if existing_links.get(apt_seq) and existing_links[apt_seq] != {candidate["id"]}:
            stats["APT_SEQ_EXISTING_CONFLICT"] += 1
            continue
        plan.append((int(trade_id), apt_seq, candidate["id"],
                     candidate["zone_id"]))
    return sorted(plan)


def months_with_stored_trades(db_by_key):
    return sorted({
        (row["district_code"], row["deal_date"][:7].replace("-", ""))
        for rows in db_by_key.values() for row in rows
    })


def collect_plan(service_key, db_by_key, by_legal_code, cache_dir=None):
    observations = defaultdict(list)
    existing_links = defaultdict(set)
    stats = Counter()
    requests = months_with_stored_trades(db_by_key)
    for index, (district, month) in enumerate(requests, 1):
        for item in load_cached_detail(service_key, district, month,
                                       cache_dir):
            key = trade_key_from_api(item)
            rows = db_by_key.get(key, ())
            if not rows:
                stats["NO_EXACT_DB_KEY"] += 1
                continue
            if len(rows) != 1:
                stats["AMBIGUOUS_DB_KEY"] += 1
                continue
            row = rows[0]
            apt_seq = (item.findtext("aptSeq") or "").strip()
            if not apt_seq:
                stats["MISSING_APT_SEQ"] += 1
                continue
            if row["complex_id"]:
                existing_links[apt_seq].add(row["complex_id"])
                continue
            candidate, status = candidate_for(item, row, by_legal_code)
            stats[status] += 1
            if candidate:
                observations[row["id"]].append((apt_seq, candidate))
        if index % 50 == 0:
            print(f"상세 API 대조: {index}/{len(requests)} 시군구·월",
                  flush=True)
    plan = decide_plan(observations, existing_links, stats)
    return plan, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--container", default="geupjido-postgres")
    parser.add_argument("--from-month", default="202510")
    parser.add_argument("--through-month", default="202610")
    parser.add_argument("--cache-dir", type=Path,
                        default=ROOT / "data/trade/import-runs/detail-apt-seq-cache")
    args = parser.parse_args()
    for month in (args.from_month, args.through_month):
        if not re.fullmatch(r"\d{4}(0[1-9]|1[0-2])", month):
            parser.error("months must use yyyyMM")
    if args.from_month > args.through_month:
        parser.error("from-month must not follow through-month")
    db_by_key, by_legal_code = load_db(args.container, args.from_month,
                                       args.through_month)
    plan, stats = collect_plan(read_service_key(ROOT / ".env"),
                               db_by_key, by_legal_code, args.cache_dir)
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
