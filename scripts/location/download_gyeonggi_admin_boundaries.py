"""2026-10-01 행정동 경계에서 경기도 구역을 내려받아 보관한다.

원본 GeoJSON은 Git에서 제외한다. 네트워크 없이 재검증할 때는
--source-file에 같은 버전의 원본 GeoJSON을 지정한다.
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
OUTPUT = ROOT / "data/location/boundary/gyeonggi-admin-dong-boundaries-20261001.geojson"


def select_gyeonggi(payload: dict) -> dict:
    if payload.get("type") != "FeatureCollection":
        raise ValueError("행정동 경계 원본이 FeatureCollection이 아닙니다")
    selected = [
        feature for feature in payload.get("features", [])
        if str(feature.get("properties", {}).get("adm_cd2", "")).startswith("41")
    ]
    if not selected:
        raise ValueError("경기도 행정동 경계가 없습니다")
    if any(
        feature.get("geometry", {}).get("type") not in {"Polygon", "MultiPolygon"}
        for feature in selected
    ):
        raise ValueError("경기도 행정동 경계의 geometry 형식이 잘못됐습니다")
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
    selected = select_gyeonggi(payload)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(selected, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"경기도 행정동 경계 {len(selected['features'])}개 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
