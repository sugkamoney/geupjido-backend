"""승인된 경기 권역표를 법정동 코드 기반 매핑으로 컴파일한다.

다산지금은 다산진건과 법정동이 같으므로 별도 공식 경계 단계에서 추가한다.
생활권 일부만 포함하는 나머지 권역도 후속 경계 단계에서 폴리곤을 좁힌다.
"""

from __future__ import annotations

import csv
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
DECISIONS = ROOT / "data/location/mapping/gyeonggi-zone-decisions.tsv"
REFERENCE = ROOT / "data/location/reference/legal-dongs-20260917.tsv"

# 지명만으로 법정동을 확정할 수 없는 2026년 행정동/사업지구 생활권.
# 해당 법정동 전체를 서비스 권역으로 쓰지 않고 후속 공식 경계로 잘라 낸다.
SPECIAL_COMPONENTS = {
    ("하남시", "위례"): "학암동·감이동",
    ("하남시", "미사"): "미사동·망월동·선동·풍산동",
    ("평택시", "고덕"): "고덕동",
    ("안양시", "인덕원"): "동안구 관양동",
    ("시흥시", "목감"): "목감동·조남동·산현동·논곡동·물왕동",
    ("파주시", "운정"): "교하동·야당동·다율동·상지석동·동패동·당하동·목동동·와동동",
    ("파주시", "금촌"): "금촌동·아동동·금릉동",
    ("남양주시", "다산진건"): "다산동",
    ("남양주시", "덕소"): "와부읍",
}
SPECIAL_OUTSIDE_MAPPING = {
    ("남양주시", "다산지금"),
    ("하남시", "감이"),
    ("안양시", "관양"),
    ("남양주시", "다산기타"),
}


def load_reference() -> list[tuple[str, list[str]]]:
    rows = []
    with REFERENCE.open(encoding="utf-8") as source:
        next(source)
        for line in source:
            code, full_name = line.rstrip("\n").split("\t", 1)
            rows.append((code, full_name.split()))
    return rows


def find_code(reference: list[tuple[str, list[str]]], city: str,
              gu: str | None, dong: str) -> str:
    matches = [
        code for code, parts in reference
        if len(parts) >= 3 and parts[0] == "경기도" and parts[1] == city
        and parts[-1] == dong and (gu is None or parts[-2] == gu)
    ]
    if len(matches) != 1:
        raise ValueError(f"법정동 코드가 유일하지 않습니다: {city} {gu or ''} {dong}: {matches}")
    return matches[0]


def resolve_codes(reference: list[tuple[str, list[str]]], city: str,
                  components: str) -> list[str]:
    gu = None
    codes = []
    for component in components.split("·"):
        parts = component.strip().split()
        if len(parts) == 2:
            gu, dong = parts
        elif len(parts) == 1:
            dong = parts[0]
        else:
            raise ValueError(f"법정동 표기가 잘못되었습니다: {components}")
        codes.append(find_code(reference, city, gu, dong))
    return codes


def build_mapping(mapping: dict, rows: list[dict],
                  reference: list[tuple[str, list[str]]]) -> dict:
    if len(rows) != 132:
        raise ValueError(f"확장된 경기 권역 132개 중 {len(rows)}개가 있습니다")
    seoul = [z for z in mapping["zones"] if z["cityId"].startswith("11")]
    incheon = [z for z in mapping["zones"] if z["cityId"].startswith("28")]
    old_cities = {c["id"]: c for c in mapping["cities"]}
    used_codes = set()
    zones = []

    for index, row in enumerate(rows, start=1):
        city, name = row["시군구"], row["권역명"]
        if (city, name) in SPECIAL_OUTSIDE_MAPPING:
            continue
        components = SPECIAL_COMPONENTS.get(
            (city, name), row["포함_법정동_또는_리"]
        )
        codes = resolve_codes(reference, city, components)
        overlap = used_codes.intersection(codes)
        if overlap:
            raise ValueError(f"법정동 코드 중복: {city} {name}: {overlap}")
        used_codes.update(codes)
        city_ids = {code[:5] for code in codes}
        if len(city_ids) != 1:
            raise ValueError(f"한 권역이 서로 다른 시군구 코드를 가집니다: {city} {name}")
        city_id = city_ids.pop()
        zones.append({
            "id": f"gg-{city_id}-{index:03d}",
            "cityId": city_id,
            "name": name,
            "dongCodes": codes,
            "initialTier": float(row["초기급지"]),
        })
        if city_id not in old_cities:
            full = next(parts for code, parts in reference if code[:5] == city_id)
            old_cities[city_id] = {
                "id": city_id,
                "regionCode": "gyeonggi",
                "name": " ".join(full[1:-1]),
            }

    if len(zones) != 128:
        raise ValueError(f"경기 일반 권역 128개 중 {len(zones)}개가 있습니다")
    cities = [c for c in mapping["cities"] if c["regionCode"] != "gyeonggi"]
    for city_data in old_cities.values():
        if city_data["regionCode"] != "gyeonggi":
            continue
        full = next(parts for code, parts in reference if code[:5] == city_data["id"])
        cities.append({**city_data, "name": " ".join(full[1:-1])})
    return {**mapping, "cities": cities, "zones": seoul + zones + incheon}


def main() -> None:
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    with DECISIONS.open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source, delimiter="\t"))
    result = build_mapping(mapping, rows, load_reference())
    MAPPING.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print("경기 일반 권역 128개, 부분 경계로 추가할 권역 4개")


if __name__ == "__main__":
    main()
