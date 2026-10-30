#!/usr/bin/env python3
"""검증된 단지의 분양권·입주권 원본을 별도 테이블에 적재한다.

기본 실행은 조회만 한다. --apply는 조회한 계약월 범위를 한 트랜잭션에서
교체하며, 일반 apartment_trade와 complex.price는 수정하지 않는다.
"""

import argparse
import csv
import io
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from audit_unpriced_complexes import REFERENCE, ROOT, normalized_lot, parcel, read_db


API_URL = (
    "https://apis.data.go.kr/1613000/RTMSDataSvcSilvTrade/"
    "getRTMSDataSvcSilvTrade"
)
COLUMNS = (
    "complex_id", "zone_id", "district_code", "deal_year_month",
    "source_ordinal", "deal_date", "deal_amount", "exclusive_area",
    "floor", "legal_dong_name", "raw_apartment_name", "lot_number",
    "ownership_gbn", "is_canceled", "cancellation_date",
)


def service_key():
    key = os.environ.get("DATA_GO_KR_SERVICE_KEY")
    if not key:
        for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
            if line.startswith("DATA_GO_KR_SERVICE_KEY="):
                key = line.split("=", 1)[1].strip().strip('"').strip("'")
                break
    if not key:
        raise ValueError("DATA_GO_KR_SERVICE_KEY가 필요합니다.")
    return urllib.parse.unquote(key)


def month_range(start, end):
    for value in (start, end):
        if not re.fullmatch(r"\d{4}(0[1-9]|1[0-2])", value):
            raise ValueError("계약월은 yyyyMM 형식이어야 합니다.")
    if start > end:
        raise ValueError("시작 계약월이 종료 계약월보다 늦습니다.")
    year, month = int(start[:4]), int(start[4:])
    while f"{year:04}{month:02}" <= end:
        yield f"{year:04}{month:02}"
        year, month = (year + 1, 1) if month == 12 else (year, month + 1)


def fetch_month(key, district, month):
    items = []
    page = 1
    while True:
        query = urllib.parse.urlencode({
            "serviceKey": key, "LAWD_CD": district, "DEAL_YMD": month,
            "numOfRows": 1000, "pageNo": page,
        })
        request = urllib.request.Request(
            API_URL + "?" + query, headers={"Accept": "application/xml"}
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                root = ET.fromstring(response.read())
        except urllib.error.HTTPError as error:
            raise ValueError(
                f"분양권 API HTTP 오류: {district}/{month}, 상태={error.code}"
            ) from None
        except urllib.error.URLError:
            raise ValueError(
                f"분양권 API 네트워크 오류: {district}/{month}"
            ) from None
        code = (root.findtext(".//resultCode") or "").strip()
        if code != "000":
            raise ValueError(f"분양권 API 오류: {district}/{month}, 코드={code}")
        total = int(root.findtext(".//totalCount") or "0")
        page_items = root.findall(".//item")
        if not page_items and len(items) < total:
            raise ValueError(f"분양권 API 응답이 부족합니다: {district}/{month}")
        items.extend(page_items)
        if len(items) >= total:
            if len(items) != total:
                raise ValueError(f"분양권 API 건수가 맞지 않습니다: {district}/{month}")
            return items
        page += 1


def normalized_name(value):
    return re.sub(r"\s+", "", value or "").removesuffix("아파트")


def parse_cancellation(item):
    cancel_type = (item.findtext("cdealType") or "").strip().upper()
    if cancel_type not in ("", "O"):
        raise ValueError("알 수 없는 거래 해제 구분입니다.")
    canceled = cancel_type == "O"
    raw_date = (item.findtext("cdealDay") or "").strip()
    if not canceled:
        return False, None
    if not raw_date:
        raise ValueError("해제 거래에 해제일이 없습니다.")
    for fmt in ("%y.%m.%d", "%Y-%m-%d", "%Y.%m.%d"):
        try:
            return True, datetime.strptime(raw_date, fmt).date().isoformat()
        except ValueError:
            pass
    raise ValueError("해제일 형식을 알 수 없습니다.")


def parse_matched_item(item, ordinal, month, complex_row, legal_name, lot):
    if (item.findtext("sggCd") or "").strip() != complex_row["legal_dong_code"][:5]:
        return None
    if (item.findtext("umdNm") or "").strip() != legal_name:
        return None
    if normalized_lot(item.findtext("jibun")) != lot:
        return None
    if normalized_name(item.findtext("aptNm")) != normalized_name(complex_row["name"]):
        return None

    deal_date = date(
        int(item.findtext("dealYear")), int(item.findtext("dealMonth")),
        int(item.findtext("dealDay"))
    )
    if deal_date.strftime("%Y%m") != month:
        raise ValueError(f"요청 계약월과 거래 계약월이 다릅니다: {month}")
    canceled, cancellation_date = parse_cancellation(item)
    amount = int((item.findtext("dealAmount") or "").replace(",", "").strip())
    area = (item.findtext("excluUseAr") or "").strip()
    if amount <= 0 or not area:
        raise ValueError("거래금액 또는 전용면적이 올바르지 않습니다.")
    floor = (item.findtext("floor") or "").strip()
    return (
        complex_row["id"], complex_row["zone_id"],
        complex_row["legal_dong_code"][:5], month, ordinal,
        deal_date.isoformat(), amount, area, int(floor) if floor else None,
        legal_name, (item.findtext("aptNm") or "").strip(),
        (item.findtext("jibun") or "").strip(),
        (item.findtext("ownershipGbn") or "").strip() or None,
        canceled, cancellation_date,
    )


def replace_rows(container, complex_id, district, months, rows):
    output = io.StringIO()
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(COLUMNS)
    for row in rows:
        writer.writerow(row)
    fields = ", ".join(COLUMNS)
    month_list = ", ".join(f"'{month}'" for month in months)
    sql = (
        "BEGIN;\n"
        f"CREATE TEMP TABLE incoming_presale "
        f"(LIKE apartment_presale_trade INCLUDING DEFAULTS) ON COMMIT DROP;\n"
        f"COPY incoming_presale ({fields}) FROM STDIN WITH (FORMAT csv, HEADER true);\n"
        + output.getvalue() + "\\.\n"
        f"DELETE FROM apartment_presale_trade WHERE complex_id = '{complex_id}' "
        f"AND district_code = '{district}' AND deal_year_month IN ({month_list});\n"
        f"INSERT INTO apartment_presale_trade ({fields}) "
        f"SELECT {fields} FROM incoming_presale;\n"
        "COMMIT;\n"
    )
    completed = subprocess.run(
        ["docker", "exec", "-i", "-u", "postgres", container, "psql",
         "-v", "ON_ERROR_STOP=1", "-d", "geupjido"],
        input=sql, text=True, capture_output=True,
    )
    if completed.returncode:
        raise RuntimeError(completed.stderr.strip() or "분양권 원본 저장 실패")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--complex-id", required=True)
    parser.add_argument("--from-month", default="202401")
    parser.add_argument("--through-month", default=datetime.now(
        ZoneInfo("Asia/Seoul")).strftime("%Y%m"))
    parser.add_argument("--container", default="geupjido-postgres")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"A\d{8}", args.complex_id):
        parser.error("단지 ID는 A와 숫자 8자리여야 합니다.")
    months = list(month_range(args.from_month, args.through_month))
    candidates = read_db(args.container,
        "SELECT id, name, legal_dong_code, address_jibun, zone_id, sale_type "
        f"FROM complex WHERE id = '{args.complex_id}'")
    if len(candidates) != 1:
        raise ValueError("단지를 찾을 수 없습니다.")
    complex_row = candidates[0]
    if not complex_row["zone_id"]:
        raise ValueError("단지 권역이 없습니다.")
    sale_type = complex_row["sale_type"] or ""
    if "임대" in sale_type and "분양" not in sale_type and "혼합" not in sale_type:
        raise ValueError("임대 전용 단지는 대상이 아닙니다.")
    with REFERENCE.open(encoding="utf-8") as file:
        legal_names = {row["법정동코드"]: row["법정동명"].rsplit(" ", 1)[-1]
                       for row in csv.DictReader(file, delimiter="\t")}
    legal_name = legal_names.get(complex_row["legal_dong_code"])
    lot = parcel(complex_row["address_jibun"], legal_name) if legal_name else None
    if not lot:
        raise ValueError("단지의 법정동 또는 지번을 확인할 수 없습니다.")
    key = service_key()
    rows = []
    for month in months:
        items = fetch_month(key, complex_row["legal_dong_code"][:5], month)
        rows.extend(parsed for ordinal, item in enumerate(items)
                    if (parsed := parse_matched_item(
                        item, ordinal, month, complex_row, legal_name, lot
                    )) is not None)
    canceled = sum(row[13] for row in rows)
    print(f"단지={args.complex_id}, 계약월={months[0]}~{months[-1]}, "
          f"원본={len(rows)}건, 해제 표기={canceled}건")
    if args.apply:
        replace_rows(args.container, args.complex_id,
                     complex_row["legal_dong_code"][:5], months, rows)
        print("별도 분양권·입주권 테이블에 저장했습니다.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError) as error:
        print("실패:", error, file=sys.stderr)
        sys.exit(1)
