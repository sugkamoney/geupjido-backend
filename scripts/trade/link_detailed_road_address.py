#!/usr/bin/env python3
"""상세 실거래가 API의 도로명 주소를 근거로 지번 불일치 거래를 연결한다.

현재 로컬 PostgreSQL Docker 컨테이너의 초기 적재를 위한 수동 도구다.
기본 실행은 감사만 수행한다. --apply를 명시하면 검증된 거래만 갱신한다.
"""

import argparse
import csv
import io
import re
import subprocess
import sys
from time import sleep
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlencode
from urllib.error import HTTPError, URLError
from urllib.request import urlopen
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
API_URL = (
    "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/"
    "getRTMSDataSvcAptTradeDev"
)


def read_service_key(path):
    for line in path.read_text().splitlines():
        if line.lstrip().startswith("#") or "=" not in line:
            continue
        name, _, value = line.partition("=")
        if name.strip() == "DATA_GO_KR_SERVICE_KEY":
            return value.strip().strip('"').strip("'")
    raise ValueError(".env에 DATA_GO_KR_SERVICE_KEY가 없습니다.")


def read_db(container, query):
    result = subprocess.run(
        ["docker", "exec", "-u", "postgres", container, "psql",
         "-U", "postgres", "-d", "geupjido", "-c",
         "COPY (" + query + ") TO STDOUT WITH CSV HEADER"],
        check=True, capture_output=True, text=True,
    )
    return list(csv.DictReader(io.StringIO(result.stdout)))


def trade_key_from_db(row):
    return (
        row["district_code"], row["deal_date"],
        row["raw_apartment_name"].strip(), (row["lot_number"] or "").strip(),
        int(row["deal_amount"]), Decimal(row["exclusive_area"]),
        int(row["floor"]) if row["floor"] else None,
        int(row["build_year"]) if row["build_year"] else None,
        row["is_canceled"] == "t",
    )


def trade_key_from_api(item):
    value = lambda field: (item.findtext(field) or "").strip()
    return (
        value("sggCd"),
        f"{int(value('dealYear')):04}-{int(value('dealMonth')):02}-"
        f"{int(value('dealDay')):02}",
        value("aptNm"), value("jibun"),
        int(value("dealAmount").replace(",", "")),
        Decimal(value("excluUseAr")),
        int(value("floor")) if value("floor") else None,
        int(value("buildYear")) if value("buildYear") else None,
        value("cdealType") == "O",
    )


def address_matches(item, candidate_address):
    road_name = (item.findtext("roadNm") or "").strip()
    main = (item.findtext("roadNmBonbun") or "").strip()
    sub = (item.findtext("roadNmBubun") or "").strip()
    if not road_name or not main or not main.isdecimal():
        return False
    number = str(int(main))
    if sub and sub.isdecimal() and int(sub) > 0:
        number += "-" + str(int(sub))
    expression = re.compile(
        r"(?:^|\s)" + re.escape(road_name) + r"\s+"
        + re.escape(number) + r"(?=\s|$)"
    )
    return bool(expression.search(candidate_address))


def fetch_detail(service_key, district, month):
    page = 1
    result = []
    while True:
        params = urlencode({
            "serviceKey": service_key, "LAWD_CD": district,
            "DEAL_YMD": month, "pageNo": page, "numOfRows": 1000,
        })
        for attempt in range(3):
            try:
                with urlopen(API_URL + "?" + params, timeout=30) as response:
                    root = ET.fromstring(response.read())
                break
            except HTTPError as error:
                if error.code < 500 or attempt == 2:
                    raise RuntimeError(
                        f"상세 API HTTP 오류: 시군구={district}, "
                        f"계약월={month}, 상태={error.code}"
                    ) from None
            except URLError:
                if attempt == 2:
                    raise RuntimeError(
                        f"상세 API 연결 오류: 시군구={district}, 계약월={month}"
                    ) from None
            sleep(attempt + 1)
        code = root.findtext(".//resultCode")
        if code != "000":
            raise RuntimeError(
                f"상세 API 응답 오류: 시군구={district}, 계약월={month}, 코드={code}"
            )
        total = int(root.findtext(".//totalCount") or 0)
        items = root.findall(".//item")
        if not items and len(result) < total:
            raise RuntimeError(
                f"상세 API가 중간에 빈 페이지를 반환했습니다: {district}, {month}"
            )
        result.extend(items)
        if len(result) >= total:
            return result
        page += 1


def load_state(container, groups_path):
    with groups_path.open(encoding="utf-8") as file:
        groups = list(csv.DictReader(file, delimiter="\t"))
    required = {
        "시군구코드", "법정동코드", "법정동명", "거래지번", "거래단지명",
        "후보단지ID", "후보단지명", "후보도로명주소", "후보사용승인일",
        "권역ID",
    }
    if not groups or not required.issubset(groups[0]):
        raise ValueError("주소 검토 파일의 열 구성이 예상과 다릅니다.")
    group_by_key = {}
    for group in groups:
        key = (group["시군구코드"], group["법정동명"],
               group["거래단지명"], group["거래지번"])
        if key in group_by_key:
            raise ValueError("주소 검토 파일에 중복 조합이 있습니다: " + str(key))
        group_by_key[key] = group

    complexes = read_db(container,
        "SELECT id, name, address_road, approval_date, legal_dong_code, "
        "zone_id, sale_type FROM complex")
    complex_by_id = {row["id"]: row for row in complexes}
    road_counts = Counter((row["legal_dong_code"], row["address_road"])
                          for row in complexes)
    trades = read_db(container,
        "SELECT id, complex_id, zone_id, deal_date, deal_amount, "
        "exclusive_area, floor, build_year, district_code, "
        "legal_dong_name, raw_apartment_name, lot_number, is_canceled "
        "FROM apartment_trade WHERE deal_date >= '2025-10-01' "
        "AND deal_date < '2026-11-01'")
    trade_by_key = defaultdict(list)
    for row in trades:
        trade_by_key[trade_key_from_db(row)].append(row)
    return group_by_key, complex_by_id, road_counts, trade_by_key


def assess_item(item, group, complex_by_id, road_counts, db_row):
    candidate = complex_by_id.get(group["후보단지ID"])
    if candidate is None:
        return "MISSING_COMPLEX"
    if (candidate["name"] != group["후보단지명"]
            or candidate["address_road"] != group["후보도로명주소"]
            or candidate["legal_dong_code"] != group["법정동코드"]
            or candidate["zone_id"] != group["권역ID"]
            or candidate["approval_date"] != group["후보사용승인일"]):
        return "CHANGED_COMPLEX"
    if candidate["sale_type"] and "임대" in candidate["sale_type"] \
            and "분양" not in candidate["sale_type"] \
            and "혼합" not in candidate["sale_type"]:
        return "RENTAL_ONLY"
    if road_counts[(candidate["legal_dong_code"],
                    candidate["address_road"])] != 1:
        return "SHARED_ROAD_ADDRESS"
    api_legal_code = (item.findtext("sggCd") or "") \
        + (item.findtext("umdCd") or "")
    if api_legal_code != group["법정동코드"]:
        return "LEGAL_DONG_MISMATCH"
    if db_row["legal_dong_name"] != group["법정동명"]:
        return "LEGAL_DONG_NAME_MISMATCH"
    trade_name = "".join((db_row.get("raw_apartment_name") or "").split())
    candidate_name = "".join((candidate["name"] or "").split())
    if not trade_name or trade_name != candidate_name:
        return "NAME_REVIEW"
    if not address_matches(item, candidate["address_road"]):
        return "ROAD_MISMATCH"
    return "MATCHED"


def collect_plan(service_key, groups, complex_by_id, road_counts, trade_by_key):
    district_months = sorted({
        (row["district_code"], row["deal_date"][:7].replace("-", ""))
        for rows in trade_by_key.values() for row in rows
        if (row["district_code"], row["legal_dong_name"],
            row["raw_apartment_name"], row["lot_number"]) in groups
    })
    matched_rows = defaultdict(list)
    observed = defaultdict(set)
    existing_links = defaultdict(set)
    stats = Counter()
    for index, (district, month) in enumerate(district_months, 1):
        for item in fetch_detail(service_key, district, month):
            key = trade_key_from_api(item)
            matches = trade_by_key.get(key, ())
            if len(matches) != 1:
                if matches:
                    stats["ambiguous_db_key"] += 1
                continue
            db_row = matches[0]
            apt_seq = (item.findtext("aptSeq") or "").strip()
            if not apt_seq:
                stats["missing_apt_seq"] += 1
                continue
            if db_row["complex_id"]:
                existing_links[apt_seq].add(db_row["complex_id"])
                continue
            group_key = (db_row["district_code"], db_row["legal_dong_name"],
                         db_row["raw_apartment_name"], db_row["lot_number"])
            group = groups.get(group_key)
            if group is None:
                continue
            status = assess_item(item, group, complex_by_id,
                                 road_counts, db_row)
            stats[status] += 1
            if status == "MATCHED":
                observed[group_key].add(apt_seq)
                matched_rows[db_row["id"]].append(
                    (apt_seq, group["후보단지ID"], group["권역ID"],
                     group_key)
                )
        if index % 50 == 0:
            print(f"상세 API 대조: {index}/{len(district_months)} 시군구·월",
                  flush=True)

    planned_links = defaultdict(set)
    for matches in matched_rows.values():
        for apt_seq, complex_id, _, _ in matches:
            planned_links[apt_seq].add(complex_id)

    plan = []
    for trade_id, matches in matched_rows.items():
        if len(matches) != 1:
            stats["duplicate_api_key"] += 1
            continue
        apt_seq, complex_id, zone_id, group_key = matches[0]
        if len(observed[group_key]) != 1:
            stats["multiple_apt_seq_in_group"] += 1
            continue
        if planned_links[apt_seq] != {complex_id}:
            stats["apt_seq_candidate_conflict"] += 1
            continue
        if existing_links.get(apt_seq) and existing_links[apt_seq] != {complex_id}:
            stats["apt_seq_conflict"] += 1
            continue
        plan.append((int(trade_id), apt_seq, complex_id, zone_id))
    return sorted(plan), stats, len(district_months)


def apply_plan(container, plan):
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    for trade_id, _, complex_id, zone_id in plan:
        writer.writerow((trade_id, complex_id, zone_id))
    sql = (
        "\\set ON_ERROR_STOP on\n"
        "BEGIN;\n"
        "CREATE TEMP TABLE link_plan "
        "(id BIGINT PRIMARY KEY, complex_id VARCHAR(50), zone_id VARCHAR(100));\n"
        "COPY link_plan (id, complex_id, zone_id) FROM STDIN WITH CSV;\n"
        + buffer.getvalue() + "\\.\n"
        "DO $$ DECLARE changed INTEGER; expected INTEGER; BEGIN\n"
        "SELECT count(*) INTO expected FROM link_plan;\n"
        "UPDATE apartment_trade t SET complex_id = p.complex_id, "
        "zone_id = p.zone_id FROM link_plan p WHERE t.id = p.id "
        "AND t.complex_id IS NULL AND t.zone_id IS NULL;\n"
        "GET DIAGNOSTICS changed = ROW_COUNT;\n"
        "IF changed <> expected THEN RAISE EXCEPTION "
        "'계획 행 수 %와 갱신 행 수 %가 다릅니다', expected, changed; END IF;\n"
        "END $$;\nCOMMIT;\n"
    )
    result = subprocess.run(
        ["docker", "exec", "-i", "-u", "postgres", container,
         "psql", "-U", "postgres", "-d", "geupjido"],
        input=sql, text=True, capture_output=True,
    )
    if result.returncode:
        raise RuntimeError("DB 갱신이 실패했습니다: " + result.stderr)
    print("DB 갱신 완료:", len(plan), "건")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--container", default="geupjido-postgres")
    parser.add_argument("--groups", type=Path,
                        default=ROOT / "build/reports/trade-address-review-groups.tsv")
    args = parser.parse_args()
    if not args.groups.is_file():
        parser.error("주소 검토 파일이 없습니다: " + str(args.groups))
    groups, complex_by_id, road_counts, trade_by_key = load_state(
        args.container, args.groups)
    plan, stats, request_count = collect_plan(
        read_service_key(ROOT / ".env"), groups,
        complex_by_id, road_counts, trade_by_key,
    )
    print("대조 범위:", request_count, "시군구·월")
    print("검증 통계:", dict(stats))
    print("연결 가능:", len(plan), "건")
    if args.apply and plan:
        apply_plan(args.container, plan)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, ET.ParseError) as error:
        print("실패:", error, file=sys.stderr)
        sys.exit(1)
