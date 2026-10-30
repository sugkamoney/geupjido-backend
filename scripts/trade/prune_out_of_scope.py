#!/usr/bin/env python3
"""현재 권역 법정동 밖의 로컬 매매 거래와 해당 충돌 원본을 정리한다."""

import argparse
import csv
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "data/location/reference/legal-dongs-20260917.tsv"
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
CONTAINER = "geupjido-postgres"


def query(sql):
    result = subprocess.run(
        ["docker", "exec", "-u", "postgres", CONTAINER,
         "psql", "-d", "geupjido", "-At", "-F", "\t", "-c", sql],
        capture_output=True, text=True, check=True,
    )
    return [line.split("\t") for line in result.stdout.splitlines()]


def classify(district, dong, reference, service_codes):
    code = reference.get((district, dong))
    if code is None and " " in dong:
        town = dong.split(" ", 1)[0]
        if town.endswith(("읍", "면")):
            code = reference.get((district, town))
    if code is None:
        return "unknown"
    return "inside" if code in service_codes else "outside"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    with REFERENCE.open(encoding="utf-8") as source:
        reader = csv.DictReader(source, delimiter="\t")
        reference = {
            (row["법정동코드"][:5], row["법정동명"].split()[-1]):
                row["법정동코드"] for row in reader
        }
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    service_codes = {
        code for zone in mapping["zones"] for code in zone["dongCodes"]
    }
    db_codes = {
        row[0] for row in query("SELECT DISTINCT unnest(dong_codes) FROM zone")
    }
    if service_codes != db_codes:
        raise RuntimeError("DB와 파일의 서비스 법정동 범위가 다릅니다")

    grouped = query(
        "SELECT district_code, legal_dong_name, count(*) "
        "FROM apartment_trade GROUP BY 1, 2"
    )
    outside = []
    counts = {"inside": 0, "outside": 0, "unknown": 0}
    for district, dong, amount in grouped:
        status = classify(district, dong, reference, service_codes)
        counts[status] += int(amount)
        if status == "outside":
            outside.append((district, dong))

    print(f"서비스 안 {counts['inside']:,}건, 밖 {counts['outside']:,}건, "
          f"법정동 미확인 {counts['unknown']:,}건")
    print(f"삭제 대상 거래 조합 {len(outside):,}개")
    if not args.apply:
        return

    if not outside:
        print("삭제 대상이 없습니다")
        return

    backup = ("/tmp/geupjido-before-scope-prune-" +
              datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + ".dump")
    subprocess.run(
        ["docker", "exec", "-u", "postgres", CONTAINER,
         "pg_dump", "-Fc", "-d", "geupjido", "-f", backup],
        check=True,
    )
    print(f"DB 백업: 컨테이너 {backup}")

    # DELETE는 단일 트랜잭션에서 진행한다. 연결된 거래나 다른 범위의 충돌을
    # 참조하는 행이 있으면 전체를 롤백한다.
    script = [
        "BEGIN;",
        "CREATE TEMP TABLE outside_keys (district_code text, legal_dong_name text, "
        "PRIMARY KEY (district_code, legal_dong_name)) ON COMMIT DROP;",
        "COPY outside_keys (district_code, legal_dong_name) FROM STDIN;",
    ]
    for district, dong in outside:
        if any(character in district + dong for character in "\t\n\r\\"):
            raise RuntimeError("COPY에 안전하지 않은 법정동 값이 있습니다")
        script.append(f"{district}\t{dong}")
    script += [
        "\\.",
        "DO $$ BEGIN "
        "IF (SELECT count(*) FROM apartment_trade t JOIN outside_keys k "
        "USING (district_code, legal_dong_name)) != " + str(counts["outside"]) +
        " THEN RAISE EXCEPTION '거래 수가 사전 검사와 다릅니다'; END IF; "
        "IF EXISTS (SELECT 1 FROM apartment_trade t JOIN outside_keys k "
        "USING (district_code, legal_dong_name) "
        "WHERE t.complex_id IS NOT NULL OR t.zone_id IS NOT NULL) "
        "THEN RAISE EXCEPTION '연결된 거래는 삭제할 수 없습니다'; END IF; "
        "IF EXISTS (SELECT 1 FROM apartment_trade_conflict c "
        "JOIN apartment_trade t ON t.id = c.existing_trade_id "
        "JOIN outside_keys k ON k.district_code = t.district_code "
        "AND k.legal_dong_name = t.legal_dong_name "
        "WHERE NOT EXISTS (SELECT 1 FROM outside_keys x "
        "WHERE x.district_code = c.district_code "
        "AND x.legal_dong_name = c.legal_dong_name)) "
        "THEN RAISE EXCEPTION '범위 안 충돌이 삭제 대상 거래를 참조합니다'; END IF; "
        "END $$;",
        "DELETE FROM apartment_trade_conflict c USING outside_keys k "
        "WHERE c.district_code = k.district_code "
        "AND c.legal_dong_name = k.legal_dong_name;",
        "DELETE FROM apartment_trade t USING outside_keys k "
        "WHERE t.district_code = k.district_code "
        "AND t.legal_dong_name = k.legal_dong_name;",
        "COMMIT;",
    ]
    result = subprocess.run(
        ["docker", "exec", "-i", "-u", "postgres", CONTAINER,
         "psql", "-v", "ON_ERROR_STOP=1", "-d", "geupjido"],
        input="\n".join(script) + "\n", capture_output=True, text=True,
    )
    print(result.stdout)
    if result.returncode:
        raise RuntimeError(result.stderr)
    print("정리 후 거래 수:", query("SELECT count(*) FROM apartment_trade")[0][0])


if __name__ == "__main__":
    main()
