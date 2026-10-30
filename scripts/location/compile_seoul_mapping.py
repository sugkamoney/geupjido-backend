"""승인된 서울 권역표를 기존 지역 매핑에 반영한다.

입력 표의 한 행은 하나의 지리적 권역이다. 목동 신시가지와 서부이촌처럼
한 법정동을 나누는 권역은 별도 단지·경계 배정 단계에서 처리한다.
"""

import csv
import json
from decimal import Decimal
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
DECISIONS = ROOT / "data/location/mapping/seoul-zone-decisions.tsv"


def build_mapping(mapping: dict, rows: list[dict]) -> dict:
    non_seoul = [
        zone for zone in mapping["zones"]
        if not zone["cityId"].startswith("11")
    ]
    city_ids = {
        city["name"]: city["id"]
        for city in mapping["cities"]
        if city["id"].startswith("11")
    }
    seoul = []
    seen_ids = set()
    seen_codes = set()

    for row in rows:
        city_id = city_ids[row["시군구"]]
        name = row["권역"]
        if (city_id, name) in {
            ("11170", "서부이촌"),
            ("11470", "목동 단지"),
        }:
            continue
        zone_id = row["원래 권역 ID"].split(",")[0]
        if zone_id in seen_ids:
            raise ValueError(f"중복 권역 ID: {zone_id}")
        seen_ids.add(zone_id)
        codes = row["법정동 코드"].split(",")
        duplicates = seen_codes.intersection(codes)
        if duplicates:
            raise ValueError(f"중복 법정동 코드: {duplicates}")
        seen_codes.update(codes)
        tier = Decimal(row["급지"].split()[0])
        seoul.append({
            "id": zone_id,
            "cityId": city_id,
            "name": name,
            "dongCodes": codes,
            "initialTier": float(tier),
        })

    if len(seen_codes) != 467:
        raise ValueError(f"서울 법정동 467개 중 {len(seen_codes)}개 배정")
    return {**mapping, "zones": seoul + non_seoul}


def main() -> None:
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    with DECISIONS.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    result = build_mapping(mapping, rows)
    MAPPING.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    count = sum(z["cityId"].startswith("11") for z in result["zones"])
    print(f"서울 지리 권역 {count}개, 법정동 467개")


if __name__ == "__main__":
    main()
