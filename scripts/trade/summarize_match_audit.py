#!/usr/bin/env python3
"""거래별 점검 TSV를 지역별 현황과 수동 검토 묶음으로 요약한다."""

import argparse
import csv
from collections import Counter, defaultdict
from pathlib import Path


def write_tsv(path, columns, rows):
    with path.open("w", encoding="utf-8", newline="") as output:
        writer = csv.writer(output, delimiter="\t")
        writer.writerow(columns)
        writer.writerows(rows)


def normalized_name(value):
    return "".join(value.split())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audit", type=Path, help="ApartmentTradeAuditRunner 결과 TSV")
    parser.add_argument("--output-dir", type=Path, default=Path("build/reports"))
    parser.add_argument("--complex-master", type=Path,
                        help="단지 ID·이름·지번주소·도로명주소·사용승인일·권역ID CSV")
    parser.add_argument("--eligible-trade-ids", type=Path,
                        help="정상·중개·59㎡ 거래 ID CSV")
    args = parser.parse_args()

    complexes = {}
    complexes_by_name = defaultdict(list)
    if args.complex_master:
        with args.complex_master.open(encoding="utf-8", newline="") as source:
            complexes = {row["id"]: row for row in csv.DictReader(source)}
        for complex_row in complexes.values():
            sale_type = complex_row["sale_type"]
            if "임대" in sale_type and "분양" not in sale_type:
                continue
            key = (complex_row["legal_dong_code"],
                   normalized_name(complex_row["name"]), complex_row["zone_id"])
            complexes_by_name[key].append(complex_row)
    eligible_ids = set()
    if args.eligible_trade_ids:
        with args.eligible_trade_ids.open(encoding="utf-8", newline="") as source:
            eligible_ids = {row["id"] for row in csv.DictReader(source)}

    by_district = Counter()
    review_groups = defaultdict(lambda: [0, 0, None, None])
    unlinked_groups = defaultdict(lambda: [0, 0, None, None])
    with args.audit.open(encoding="utf-8", newline="") as source:
        for row in csv.DictReader(source, delimiter="\t"):
            district = row["시군구코드"]
            scope = row["서비스범위"]
            link = row["연결상태"]
            status = row["후보판정"] if link == "UNLINKED" else ""
            by_district[(district, scope, link, status)] += 1

            if scope != "IN_SCOPE" or link != "UNLINKED":
                continue
            key = (
                district,
                row["법정동코드"],
                row["법정동명"],
                row["지번"],
                row["거래단지명"],
                row["후보단지ID"],
                row["권역ID"],
            )
            unlinked_key = (*key, status)
            unlinked_group = unlinked_groups[unlinked_key]
            unlinked_group[0] += 1
            unlinked_group[1] += row["거래ID"] in eligible_ids
            deal_date = row["계약일"]
            unlinked_group[2] = min(unlinked_group[2], deal_date) if unlinked_group[2] else deal_date
            unlinked_group[3] = max(unlinked_group[3], deal_date) if unlinked_group[3] else deal_date
            if status != "NAME_REVIEW":
                continue
            group = review_groups[key]
            group[0] += 1
            group[1] += row["거래ID"] in eligible_ids
            group[2] = min(group[2], deal_date) if group[2] else deal_date
            group[3] = max(group[3], deal_date) if group[3] else deal_date

    args.output_dir.mkdir(parents=True, exist_ok=True)
    write_tsv(
        args.output_dir / "trade-status-by-district.tsv",
        ("시군구코드", "서비스범위", "연결상태", "후보판정", "거래건수"),
        ((*key, count) for key, count in sorted(by_district.items())),
    )
    write_tsv(
        args.output_dir / "trade-name-review-groups.tsv",
        ("시군구코드", "법정동코드", "법정동명", "지번", "거래단지명",
         "후보단지ID", "후보단지명", "후보지번주소", "후보도로명주소",
         "후보사용승인일", "후보권역ID", "권역ID", "권역일치",
         "거래건수", "적격59거래건수",
         "최초계약일", "최근계약일"),
        ((*key[:6],
          *(complexes.get(key[5], {}).get(field, "") for field in
            ("name", "address_jibun", "address_road", "approval_date", "zone_id")),
          key[6],
          ("일치" if complexes[key[5]]["zone_id"] == key[6] else "불일치")
          if key[5] in complexes else "",
          *value) for key, value in sorted(
            review_groups.items(), key=lambda item: (-item[1][1], -item[1][0], item[0])
        )),
    )
    write_tsv(
        args.output_dir / "trade-unlinked-groups.tsv",
        ("시군구코드", "법정동코드", "법정동명", "지번", "거래단지명",
         "후보단지ID", "권역ID", "후보판정", "후보단지명",
         "거래건수", "적격59거래건수", "최초계약일", "최근계약일"),
        ((*key,
          ", ".join(complexes[complex_id]["name"] for complex_id in
                    key[5].split(",") if complex_id in complexes),
          *value) for key, value in sorted(
            unlinked_groups.items(),
            key=lambda item: (item[0][-1], -item[1][1], -item[1][0], item[0])
        )),
    )
    address_reviews = []
    for key, value in unlinked_groups.items():
        if key[-1] != "NO_CANDIDATE":
            continue
        district, legal_code, dong, lot, trade_name, _, zone_id, _ = key
        name_key = (legal_code, normalized_name(trade_name), zone_id)
        candidates = complexes_by_name.get(name_key, [])
        if len(candidates) != 1:
            continue
        candidate = candidates[0]
        address_reviews.append((district, legal_code, dong, lot, trade_name,
                                candidate["id"], candidate["name"],
                                candidate["address_jibun"], candidate["address_road"],
                                candidate["approval_date"], candidate["sale_type"],
                                zone_id, *value))
    address_reviews.sort(key=lambda row: (-row[13], -row[12], row[:5]))
    write_tsv(
        args.output_dir / "trade-address-review-groups.tsv",
        ("시군구코드", "법정동코드", "법정동명", "거래지번", "거래단지명",
         "후보단지ID", "후보단지명", "후보지번주소", "후보도로명주소",
         "후보사용승인일", "후보분양형태", "권역ID", "거래건수",
         "적격59거래건수", "최초계약일", "최근계약일"),
        address_reviews,
    )
    print(f"미연결 {len(unlinked_groups)}조합 중 이름 검토 {len(review_groups)}조합을 기록했습니다: {args.output_dir}")
    print(f"지번이 달라 공식 주소 검토가 필요한 동일 이름 후보 {len(address_reviews)}조합을 기록했습니다.")


if __name__ == "__main__":
    main()
