"""2026-10-01 행정동 경계에서 이촌1·2동만 내려받아 보관한다.

원본은 Git에 포함하지 않는다. 네트워크 없이 재검증할 때는 --source-file에
이미 내려받은 원본 GeoJSON을 지정할 수 있다.
"""

import argparse
import json
from pathlib import Path
from urllib.request import urlopen


ROOT = Path(__file__).resolve().parents[2]
SOURCE_URL = (
    "https://raw.githubusercontent.com/vuski/admdongkor/master/"
    "ver20261001/HangJeongDong_ver20261001.geojson"
)
OUTPUT = ROOT / "data/location/boundary/seoul-admin-dong-boundaries-20261001.geojson"
CODES = {"1117063000", "1117064000"}


def select_ichon(payload: dict) -> dict:
    if payload.get("type") != "FeatureCollection":
        raise ValueError("행정동 경계 원본이 FeatureCollection이 아닙니다")
    selected = [
        feature for feature in payload.get("features", [])
        if feature.get("properties", {}).get("adm_cd2") in CODES
    ]
    found = [feature["properties"]["adm_cd2"] for feature in selected]
    if len(found) != 2 or set(found) != CODES:
        raise ValueError("이촌1·2동 경계가 각각 하나씩 필요합니다")
    if any(
        feature.get("geometry", {}).get("type") not in {"Polygon", "MultiPolygon"}
        for feature in selected
    ):
        raise ValueError("이촌 행정동 경계의 geometry 형식이 잘못됐습니다")
    return {"type": "FeatureCollection", "features": selected}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-file", type=Path)
    args = parser.parse_args()
    if args.source_file:
        payload = json.loads(args.source_file.read_text(encoding="utf-8"))
    else:
        with urlopen(SOURCE_URL, timeout=120) as response:
            payload = json.load(response)
    selected = select_ichon(payload)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(selected, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"이촌1·2동 경계 2개 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
