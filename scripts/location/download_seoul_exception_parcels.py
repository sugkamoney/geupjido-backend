"""서울 지오코딩 예외 두 단지의 현행 필지 경계를 VWorld에서 확인한다.

도로명 검색 결과가 복수이거나 경계에서 벗어난 단지에만 사용한다.
원본 필지는 Git에서 제외하고, 필지 번호와 단지 주소를 함께 검증한다.
"""

import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "data/location/boundary/seoul-complex-exception-parcels.geojson"
CASES = {
    "A12174601": ("메세나폴리스", "1144012000", 490, 0),
    "A14074102": ("효창한신", "1117011900", 5, 145),
}


def load_env(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"환경 변수 파일이 없습니다: {path}")
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        os.environ.setdefault(name.strip(), value.strip().strip("\"'"))


def fetch_parcel(complex_id: str, dong_code: str, lot: int, sub_lot: int) -> dict:
    pnu = f"{dong_code}1{lot:04d}{sub_lot:04d}"
    params = {
        "service": "data",
        "version": "2.0",
        "request": "GetFeature",
        "format": "json",
        "size": "1",
        "page": "1",
        "geometry": "true",
        "attribute": "true",
        "crs": "EPSG:4326",
        "data": "LP_PA_CBND_BUBUN",
        "attrFilter": f"pnu:=:{pnu}",
        "key": os.environ["VWORLD_API_KEY"],
        "domain": os.environ["VWORLD_DOMAIN"],
    }
    request = Request(
        "https://api.vworld.kr/req/data?" + urlencode(params),
        headers={
            "Accept": "application/json",
            "Referer": os.environ["VWORLD_DOMAIN"],
            "User-Agent": "geupjido-zone-finalization/1.0",
        },
    )
    with urlopen(request, timeout=30) as response:
        payload = json.load(response)
    api_response = payload.get("response", {})
    if api_response.get("status") != "OK":
        raise RuntimeError(f"{complex_id} 필지 조회 실패: {api_response.get('status')}")
    features = (
        api_response.get("result", {})
        .get("featureCollection", {})
        .get("features", [])
    )
    if len(features) != 1 or features[0].get("properties", {}).get("pnu") != pnu:
        raise RuntimeError(f"{complex_id} 필지/PNU가 일치하지 않습니다: {pnu}")
    feature = features[0]
    if feature.get("geometry", {}).get("type") not in ("Polygon", "MultiPolygon"):
        raise RuntimeError(f"{complex_id} 필지 geometry 형식이 잘못되었습니다")
    feature["properties"] = {
        "complex_id": complex_id,
        "pnu": pnu,
        "address": feature["properties"].get("addr"),
    }
    return feature


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()
    load_env(args.env_file)
    features = [
        fetch_parcel(complex_id, dong_code, lot, sub_lot)
        for complex_id, (_, dong_code, lot, sub_lot) in CASES.items()
    ]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps({"type": "FeatureCollection", "features": features},
                   ensure_ascii=False),
        encoding="utf-8",
    )
    for feature in features:
        print(feature["properties"]["complex_id"], feature["properties"]["address"])
    print(f"2개 필지 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
