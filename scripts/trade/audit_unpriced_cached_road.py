#!/usr/bin/env python3
"""Cross-check unpriced complexes against cached detailed trades.

Read-only by default. --apply links uniquely verified rows in the local DB.
"""

import argparse
import csv
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

from link_detailed_road_address import (
    ROOT, address_matches, apply_plan, read_db, trade_key_from_api,
    trade_key_from_db,
)


def normalized_name(value):
    return re.sub(r"\s+", "", value or "").removesuffix("아파트")


def candidate_status(candidate, row):
    sale_type = candidate["sale_type"] or ""
    if "임대" in sale_type and "분양" not in sale_type and "혼합" not in sale_type:
        return "RENTAL_ONLY"
    if row["is_canceled"] == "t":
        return "CANCELED"
    if row["open_conflict"] == "t":
        return "OPEN_CONFLICT"
    if (sale_type and ("분양" in sale_type or "혼합" in sale_type)
            and normalized_name(candidate["name"])
            == normalized_name(row["raw_apartment_name"])):
        return "POTENTIAL_EXACT_ROAD_NAME"
    return "NAME_REVIEW"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    complexes = read_db("geupjido-postgres",
        "SELECT id, name, address_road, address_jibun, legal_dong_code, "
        "approval_date, zone_id, sale_type, price FROM complex")
    missing = {row["id"] for row in complexes if not row["price"]}
    by_code = defaultdict(list)
    for row in complexes:
        by_code[row["legal_dong_code"]].append(row)
    districts = {row["legal_dong_code"][:5] for row in complexes
                 if row["id"] in missing}
    trades = read_db("geupjido-postgres",
        "SELECT id, complex_id, zone_id, deal_date, deal_amount, "
        "exclusive_area, floor, build_year, district_code, "
        "legal_dong_name, raw_apartment_name, lot_number, is_canceled, "
        "EXISTS (SELECT 1 FROM apartment_trade_conflict x "
        "WHERE x.existing_trade_id = apartment_trade.id "
        "AND x.review_status = 'OPEN') AS open_conflict "
        "FROM apartment_trade WHERE complex_id IS NULL AND zone_id IS NULL")
    by_key = defaultdict(list)
    for row in trades:
        if row["district_code"] in districts:
            by_key[trade_key_from_db(row)].append(row)

    cache = ROOT / "data/trade/import-runs/detail-apt-seq-cache"
    observations = defaultdict(list)
    counts = Counter()
    files = sorted(path for path in cache.glob("*.xml")
                   if path.name[:5] in districts)
    for index, path in enumerate(files, 1):
        root = ET.parse(path).getroot()
        if root.tag != "items":
            raise ValueError(f"Unexpected cache format: {path}")
        for item in root.findall("item"):
            try:
                rows = by_key.get(trade_key_from_api(item), ())
            except (ValueError, TypeError):
                counts["API_KEY_PARSE_ERROR"] += 1
                continue
            if len(rows) != 1:
                continue
            row = rows[0]
            code = (item.findtext("sggCd") or "") + (item.findtext("umdCd") or "")
            if row["district_code"] != code[:5] or row["legal_dong_name"] != (item.findtext("umdNm") or "").strip():
                counts["LEGAL_DONG_MISMATCH"] += 1
                continue
            matches = [candidate for candidate in by_code[code]
                       if candidate["address_road"] and
                       address_matches(item, candidate["address_road"])]
            shared_road = len(matches) > 1
            if shared_road:
                counts["SHARED_ROAD_PARCEL_REVIEW"] += 1
                continue
            if len(matches) != 1:
                counts["NO_OR_SHARED_ROAD_MATCH"] += 1
                continue
            candidate = matches[0]
            if candidate["id"] not in missing:
                continue
            status = candidate_status(candidate, row)
            counts[status] += 1
            observations[row["id"]].append((candidate, row, item.findtext("aptSeq") or "", status))
        root.clear()
        if index % 200 == 0:
            print(f"cache files checked: {index}/{len(files)}", flush=True)

    grouped = defaultdict(list)
    for trade_id, items in observations.items():
        signatures = {(candidate["id"], apt_seq, status)
                      for candidate, _, apt_seq, status in items}
        if len(signatures) != 1:
            counts["CONFLICTING_DETAIL_MATCH"] += 1
            continue
        if len(items) > 1:
            counts["REPEATED_IDENTICAL_DETAIL_MATCH"] += 1
        candidate, row, apt_seq, status = items[0]
        grouped[candidate["id"]].append((candidate, row, apt_seq, status))

    potential_seqs = {apt_seq for items in grouped.values()
                      for _, _, apt_seq, status in items
                      if status == "POTENTIAL_EXACT_ROAD_NAME"
                      and apt_seq}
    linked_by_key = defaultdict(list)
    if potential_seqs:
        potential_districts = sorted({row["district_code"]
            for items in grouped.values() for _, row, _, status in items
            if status == "POTENTIAL_EXACT_ROAD_NAME"})
        district_filter = ",".join(f"'{code}'" for code in potential_districts)
        linked = read_db("geupjido-postgres",
            "SELECT id, complex_id, deal_date, deal_amount, exclusive_area, "
            "floor, build_year, district_code, legal_dong_name, "
            "raw_apartment_name, lot_number, is_canceled "
            "FROM apartment_trade WHERE complex_id IS NOT NULL AND "
            f"district_code IN ({district_filter})")
        for row in linked:
            linked_by_key[trade_key_from_db(row)].append(row)
    linked_ids_by_seq = defaultdict(set)
    potential_district_set = set(potential_districts) if potential_seqs else set()
    for path in files:
        if path.name[:5] not in potential_district_set:
            continue
        root = ET.parse(path).getroot()
        for item in root.findall("item"):
            seq = (item.findtext("aptSeq") or "").strip()
            if seq not in potential_seqs:
                continue
            try:
                rows = linked_by_key.get(trade_key_from_api(item), ())
            except (ValueError, TypeError):
                continue
            if len(rows) == 1:
                linked_ids_by_seq[seq].add(rows[0]["complex_id"])
        root.clear()
    output = ROOT / "data/trade/audit-runs/unpriced-cached-road.csv"
    output.parent.mkdir(parents=True, exist_ok=True)
    plan = []
    with output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(("complex_id", "complex_name", "complex_road", "complex_jibun",
                         "trade_id", "raw_trade_name", "trade_lot", "trade_build_year",
                         "deal_date", "apt_seq", "status", "apt_seq_linked_complex_ids"))
        for complex_id in sorted(grouped):
            for candidate, row, apt_seq, status in grouped[complex_id]:
                if status == "POTENTIAL_EXACT_ROAD_NAME" and linked_by_key.get(
                    trade_key_from_db(row)):
                    status = "SAME_SOURCE_ALREADY_LINKED"
                if status == "POTENTIAL_EXACT_ROAD_NAME" and linked_ids_by_seq[apt_seq]:
                    status = "APT_SEQ_ALREADY_LINKED"
                if status == "POTENTIAL_EXACT_ROAD_NAME":
                    plan.append((int(row["id"]), apt_seq, candidate["id"],
                                 candidate["zone_id"]))
                writer.writerow((candidate["id"], candidate["name"],
                    candidate["address_road"], candidate["address_jibun"],
                    row["id"], row["raw_apartment_name"], row["lot_number"],
                    row["build_year"], row["deal_date"], apt_seq, status,
                    "|".join(sorted(linked_ids_by_seq[apt_seq]))))
    potential = {complex_id for _, _, complex_id, _ in plan}
    print(f"unpriced complexes={len(missing)}, detailed cache files={len(files)}")
    print(f"unique road candidate complexes={len(grouped)}, potential complexes={len(potential)}")
    print(f"status counts={dict(counts)}")
    print("potential aptSeq existing links=", {
        seq: sorted(linked_ids_by_seq[seq]) for seq in sorted(potential_seqs)
    })
    print(f"safe unique trade rows={len(plan)}")
    print(f"report={output}")
    if args.apply and plan:
        apply_plan("geupjido-postgres", plan)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError) as error:
        print(f"audit failed: {error}", file=sys.stderr)
        sys.exit(1)
