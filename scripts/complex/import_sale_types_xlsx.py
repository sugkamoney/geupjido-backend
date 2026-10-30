#!/usr/bin/env python3
"""공식 K-apt 기본정보 엑셀에서 단지코드와 분양형태를 대조해 빈 값만 채운다."""

import argparse
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


NAMESPACE = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
VALID_SALE_TYPES = {"분양", "혼합", "임대", "사택 및 관사 등"}
CONTAINER = "geupjido-postgres"


def query(sql):
    completed = subprocess.run(
        ["docker", "exec", "-u", "postgres", CONTAINER,
         "psql", "-d", "geupjido", "-At", "-F", "\t", "-c", sql],
        capture_output=True, text=True, check=True,
    )
    return [line.split("\t") for line in completed.stdout.splitlines()]


def shared_strings(archive):
    if "xl/sharedStrings.xml" not in archive.namelist():
        return []
    result = []
    with archive.open("xl/sharedStrings.xml") as source:
        for _, element in ET.iterparse(source, events=("end",)):
            if element.tag == NAMESPACE + "si":
                result.append("".join(
                    child.text or "" for child in element.iter(NAMESPACE + "t")
                ))
                element.clear()
    return result


def rows(path):
    with zipfile.ZipFile(path) as archive:
        sheets = sorted(name for name in archive.namelist()
                        if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", name))
        if not sheets:
            raise ValueError("엑셀 시트를 찾지 못했습니다")
        strings = shared_strings(archive)
        with archive.open(sheets[0]) as source:
            for _, element in ET.iterparse(source, events=("end",)):
                if element.tag != NAMESPACE + "row":
                    continue
                cells = {}
                for cell in element.findall(NAMESPACE + "c"):
                    address = cell.get("r", "")
                    column = re.match(r"[A-Z]+", address)
                    if column is None:
                        continue
                    value = cell.find(NAMESPACE + "v")
                    if cell.get("t") == "s" and value is not None:
                        text = strings[int(value.text)]
                    elif cell.get("t") == "inlineStr":
                        text = "".join(
                            node.text or "" for node in cell.iter(NAMESPACE + "t")
                        )
                    else:
                        text = value.text if value is not None else ""
                    cells[column.group()] = (text or "").strip()
                yield int(element.get("r", "0")), cells
                element.clear()


def read_sale_types(path):
    columns = None
    found = {}
    for number, cells in rows(path):
        if columns is None:
            code_column = None
            sale_column = None
            for column, value in cells.items():
                normalized = re.sub(r"\s+", "", value).lower()
                if "단지코드" in normalized or "kaptcode" in normalized:
                    code_column = column
                if "분양형태" in normalized or "codesalenm" in normalized:
                    sale_column = column
            if number <= 30 and code_column and sale_column:
                columns = (code_column, sale_column)
                continue
            if number > 30:
                raise ValueError("첫 30행에서 단지코드·분양형태 열을 찾지 못했습니다")
            continue
        code = cells.get(columns[0], "")
        sale_type = cells.get(columns[1], "")
        if not code or not sale_type:
            continue
        if not re.fullmatch(r"[A-Za-z0-9]{1,20}", code):
            continue
        if sale_type not in VALID_SALE_TYPES:
            raise ValueError(f"예상하지 못한 분양형태: {sale_type!r} (행 {number})")
        previous = found.setdefault(code, sale_type)
        if previous != sale_type:
            raise ValueError(f"같은 단지코드의 분양형태가 다릅니다: {code}")
    if not found:
        raise ValueError("단지코드와 분양형태가 있는 행이 없습니다")
    return found


def apply_updates(updates, expected_complexes):
    backup = ("/tmp/geupjido-before-sale-type-xlsx-" +
              datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + ".dump")
    subprocess.run(
        ["docker", "exec", "-u", "postgres", CONTAINER,
         "pg_dump", "-Fc", "-d", "geupjido", "-f", backup], check=True,
    )
    print("DB 백업:", backup)
    sql = [
        "BEGIN;",
        "CREATE TEMP TABLE sale_type_input (id text PRIMARY KEY, sale_type text) "
        "ON COMMIT DROP;",
        "COPY sale_type_input (id, sale_type) FROM STDIN;",
    ]
    sql += [f"{code}\t{sale_type}" for code, sale_type in sorted(updates.items())]
    sql += [
        "\\.",
        "DO $$ BEGIN "
        f"IF (SELECT count(*) FROM complex) != {expected_complexes} "
        "THEN RAISE EXCEPTION '단지 수가 사전 검사와 다릅니다'; END IF; "
        "IF EXISTS (SELECT 1 FROM complex c JOIN sale_type_input s "
        "ON s.id=c.id WHERE c.sale_type IS NOT NULL) "
        "THEN RAISE EXCEPTION '이미 분양형태가 채워진 단지가 있습니다'; END IF; "
        "END $$;",
        "UPDATE complex c SET sale_type=s.sale_type FROM sale_type_input s "
        "WHERE c.id=s.id AND c.sale_type IS NULL;",
        "UPDATE apartment_trade t SET complex_id=NULL, zone_id=NULL "
        "WHERE t.complex_id IN (SELECT id FROM sale_type_input "
        "WHERE sale_type IN ('임대', '사택 및 관사 등'));",
        "UPDATE apartment_presale_trade t SET complex_id=NULL, zone_id=NULL "
        "WHERE t.complex_id IN (SELECT id FROM sale_type_input "
        "WHERE sale_type IN ('임대', '사택 및 관사 등'));",
        "DELETE FROM complex c USING sale_type_input s "
        "WHERE c.id=s.id AND s.sale_type IN ('임대', '사택 및 관사 등');",
        "COMMIT;",
    ]
    completed = subprocess.run(
        ["docker", "exec", "-i", "-u", "postgres", CONTAINER,
         "psql", "-v", "ON_ERROR_STOP=1", "-d", "geupjido"],
        input="\n".join(sql) + "\n", text=True,
        capture_output=True,
    )
    print(completed.stdout)
    if completed.returncode:
        raise RuntimeError(completed.stderr)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("xlsx", type=Path)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    source = read_sale_types(args.xlsx)
    db_rows = query("SELECT id, COALESCE(sale_type, '') FROM complex")
    database = {code: sale_type for code, sale_type in db_rows}
    mismatches = [code for code, sale_type in db_rows
                  if sale_type and code in source and source[code] != sale_type]
    updates = {code: source[code] for code, sale_type in db_rows
               if not sale_type and code in source}
    missing = [code for code, sale_type in db_rows
               if not sale_type and code not in source]
    print("엑셀 단지:", len(source), "DB 단지:", len(database))
    print("신규 적재 가능:", len(updates), dict(Counter(updates.values())))
    print("엑셀에서 분양형태를 확인할 수 없는 단지:", len(missing))
    print("이미 채워진 값과 충돌:", len(mismatches))
    if mismatches:
        print("충돌 단지 ID 예시:", mismatches[:20])
    if missing:
        print("분양형태 미확인 단지 ID 예시:", missing[:20])
    if not args.apply:
        return
    if mismatches:
        raise ValueError("기존 API 값과 엑셀 값이 달라 적용을 중단합니다")
    if updates:
        apply_updates(updates, len(database))


if __name__ == "__main__":
    main()
