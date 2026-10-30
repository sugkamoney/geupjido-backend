#!/usr/bin/env python3
"""List one-to-one parcel/year/name variants for manual review."""

import csv
import re
from collections import defaultdict

from audit_unpriced_complexes import ROOT, normalized_lot, read_db


def normalized_name(value):
    return re.sub(r"[\s()]+", "", value or "").removesuffix("아파트")


def main():
    path = ROOT / "data/trade/audit-runs/unpriced-complexes.csv"
    with path.open(encoding="utf-8") as file:
        unpriced = list(csv.DictReader(file))
    groups = read_db("geupjido-postgres",
        "SELECT district_code, legal_dong_name, lot_number, build_year, "
        "raw_apartment_name, count(*) AS trades, "
        "count(*) FILTER (WHERE complex_id IS NULL AND zone_id IS NULL) "
        "AS unlinked, string_agg(DISTINCT complex_id, '|' ORDER BY complex_id) "
        "AS linked_complex_ids, "
        "count(*) FILTER (WHERE NOT is_canceled AND NOT EXISTS "
        "(SELECT 1 FROM apartment_trade_conflict x "
        "WHERE x.existing_trade_id = apartment_trade.id "
        "AND x.review_status = 'OPEN')) AS eligible "
        "FROM apartment_trade GROUP BY 1,2,3,4,5")
    by_group = defaultdict(list)
    for row in groups:
        key = (row["district_code"], row["legal_dong_name"],
               normalized_lot(row["lot_number"]), row["build_year"],
               row["raw_apartment_name"])
        by_group[key].append(row)
    proposals = []
    for row in unpriced:
        if (row["reason"] != "NAME_DIFFERS_OR_SHARED_PARCEL"
                or row["sale_type"] != "분양"
                or row["same_parcel_complex_ids"] != row["complex_id"]
                or not row["approval_year"]):
            continue
        names = row["trade_names_on_parcel"].split("|")
        if len(names) != 1 or not names[0]:
            continue
        left, right = normalized_name(row["name"]), normalized_name(names[0])
        if min(len(left), len(right)) < 3 or not (left in right or right in left):
            continue
        key = (row["legal_dong_code"][:5], row["legal_dong_name"],
               row["parcel"], row["approval_year"], names[0])
        matches = by_group.get(key, [])
        if len(matches) != 1:
            continue
        group = matches[0]
        linked = group["linked_complex_ids"] or ""
        if linked and linked != row["complex_id"]:
            continue
        proposals.append((row["complex_id"], row["name"],
            row["address_jibun"], row["legal_dong_code"],
            row["approval_year"], group["raw_apartment_name"],
            group["lot_number"], group["trades"], group["unlinked"],
            group["eligible"], linked))
    output = ROOT / "data/trade/audit-runs/unique-parcel-name-variants.csv"
    with output.open("w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(("complex_id", "complex_name", "address_jibun",
            "legal_dong_code", "approval_year", "raw_trade_name",
            "trade_lot", "total_trades", "unlinked_trades",
            "eligible_trades", "linked_complex_ids"))
        writer.writerows(proposals)
    print("manual review proposals:", len(proposals))
    print("output:", output)


if __name__ == "__main__":
    main()
