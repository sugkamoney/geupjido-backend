#!/usr/bin/env python3
"""Read-only audit of complexes without a linked trade in the local database."""

import argparse
import csv
import io
import re
import subprocess
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "data/location/reference/legal-dongs-20260917.tsv"


def read_db(container, query):
    command = ["docker", "exec", "-u", "postgres", container, "psql",
               "-d", "geupjido", "-c",
               f"COPY ({query}) TO STDOUT WITH CSV HEADER"]
    completed = subprocess.run(command, text=True, capture_output=True,
                               check=True)
    return list(csv.DictReader(io.StringIO(completed.stdout)))


def normalized_lot(value):
    if not value:
        return None
    match = re.fullmatch(r"(산\s*)?(\d+)(?:-(\d*))?", value.strip())
    if not match:
        return value.strip()
    prefix = "산" if match.group(1) else ""
    return f"{prefix}{int(match.group(2))}-{int(match.group(3) or 0)}"


def normalized_name(value):
    name = re.sub(r"\s+", "", value or "")
    return name[:-3] if name.endswith("아파트") else name


def parcel(address, legal_dong_name):
    if legal_dong_name not in address:
        return None
    after = address.split(legal_dong_name, 1)[1].strip()
    match = re.match(r"^(산\s*)?\d+(?:-\d*)?(?=\s|$)", after)
    return normalized_lot(match.group()) if match else None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="geupjido-postgres")
    parser.add_argument("--output", type=Path,
                        default=ROOT / "data/trade/audit-runs/unpriced-complexes.csv")
    args = parser.parse_args()
    with REFERENCE.open(encoding="utf-8") as file:
        reference = {row["법정동코드"]: row["법정동명"]
                     for row in csv.DictReader(file, delimiter="\t")}
    complexes = read_db(args.container,
        "SELECT id, name, address_road, address_jibun, legal_dong_code, approval_date, "
        "sale_type, zone_id FROM complex WHERE price IS NULL")
    all_parcels = read_db(args.container,
        "SELECT id, address_jibun, legal_dong_code FROM complex")
    complexes_by_parcel = defaultdict(list)
    for candidate in all_parcels:
        code = candidate["legal_dong_code"] or ""
        name = reference.get(code, "").rsplit(" ", 1)[-1]
        lot = parcel(candidate["address_jibun"], name) if name else None
        if lot:
            complexes_by_parcel[(code, lot)].append(candidate["id"])
    groups = read_db(args.container,
        "SELECT district_code, legal_dong_name, lot_number, build_year, "
        "raw_apartment_name, count(*) AS trades, "
        "count(*) FILTER (WHERE complex_id IS NULL) AS unlinked, "
        "string_agg(DISTINCT complex_id, '|' ORDER BY complex_id) "
        "AS linked_complex_ids "
        "FROM apartment_trade GROUP BY 1,2,3,4,5")
    by_dong = defaultdict(list)
    by_parcel = defaultdict(list)
    by_name = defaultdict(list)
    for group in groups:
        dong = (group["district_code"], group["legal_dong_name"])
        by_dong[dong].append(group)
        by_parcel[(*dong, normalized_lot(group["lot_number"]))].append(group)
        by_name[(*dong, normalized_name(group["raw_apartment_name"]))].append(group)

    rows = []
    counts = Counter()
    for complex_row in complexes:
        legal_code = complex_row["legal_dong_code"] or ""
        legal_name = reference.get(legal_code, "").rsplit(" ", 1)[-1]
        lot = parcel(complex_row["address_jibun"], legal_name) if legal_name else None
        dong = (legal_code[:5], legal_name)
        on_parcel = by_parcel.get((*dong, lot), []) if lot else []
        year = complex_row["approval_date"][:4]
        same_name = [group for group in on_parcel
                     if normalized_name(group["raw_apartment_name"])
                     == normalized_name(complex_row["name"])]
        if not legal_name:
            reason = "NO_LEGAL_DONG_REFERENCE"
        elif not lot:
            reason = "NO_COMPLEX_PARCEL"
        elif not by_dong.get(dong):
            reason = "NO_TRADE_IN_LEGAL_DONG"
        elif not on_parcel:
            reason = "NO_TRADE_ON_PARCEL"
        elif not same_name:
            reason = "NAME_DIFFERS_OR_SHARED_PARCEL"
        elif any(int(group["unlinked"]) > 0 for group in same_name):
            reason = "NAME_PARCEL_MATCH_BUT_UNLINKED"
        elif any(complex_id != complex_row["id"]
                 for group in same_name
                 for complex_id in (group["linked_complex_ids"] or "").split("|")
                 if complex_id):
            reason = "SAME_SOURCE_LINKED_TO_OTHER_COMPLEX"
        else:
            reason = "NO_ELIGIBLE_LINKED_TRADE"
        counts[reason] += 1
        alternate_lots = sorted({normalized_lot(group["lot_number"]) or ""
            for group in by_name.get((*dong,
                normalized_name(complex_row["name"])), [])
            if normalized_lot(group["lot_number"]) != lot})
        rows.append((complex_row["id"], complex_row["name"],
                     complex_row["address_road"], complex_row["address_jibun"],
                     legal_code,
                     legal_name, lot or "", year, complex_row["sale_type"],
                     complex_row["zone_id"], reason,
                     sum(int(group["unlinked"]) for group in on_parcel),
                     "|".join(sorted({group["raw_apartment_name"]
                                      for group in on_parcel})),
                     "|".join(alternate_lots),
                     "|".join(sorted({complex_id
                         for group in same_name
                         for complex_id in
                             (group["linked_complex_ids"] or "").split("|")
                         if complex_id})),
                     "|".join(sorted(complexes_by_parcel.get(
                         (legal_code, lot), [])))))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(("complex_id", "name", "address_road",
                         "address_jibun", "legal_dong_code",
                         "legal_dong_name", "parcel", "approval_year",
                         "sale_type", "zone_id", "reason",
                         "unlinked_trades_on_parcel", "trade_names_on_parcel",
                         "alternate_lots_same_name",
                         "same_name_parcel_linked_complex_ids",
                         "same_parcel_complex_ids"))
        writer.writerows(rows)
    print(f"가격 공백 단지: {len(rows)}, 결과: {args.output}")
    for reason, count in counts.most_common():
        print(f"{reason}: {count}")


if __name__ == "__main__":
    main()
