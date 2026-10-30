"""목동신시가지 1~14단지의 현행 필지 경계를 VWorld에서 내려받는다.

원본 경계는 저장소에 포함하지 않는다. --env-file에는 VWORLD_API_KEY와
VWORLD_DOMAIN이 담긴 로컬 파일을 지정한다.
"""

import argparse
import json
import os
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "data/location/boundary/mokdong-sinjigaji-parcels.geojson"
LOTS = {
    1: ("1147010200", 901),
    2: ("1147010200", 902),
    3: ("1147010200", 903),
    4: ("1147010200", 904),
    5: ("1147010200", 912),
    6: ("1147010200", 911),
    7: ("1147010200", 925),
    8: ("1147010100", 314),
    9: ("1147010100", 312),
    10: ("1147010100", 311),
    11: ("1147010100", 325),
    12: ("1147010100", 326),
    13: ("1147010100", 327),
    14: ("1147010100", 329),
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


def fetch_parcel(number: int, dong_code: str, lot: int) -> dict:
    pnu = f"{dong_code}1{lot:04d}0000"
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
        raise RuntimeError(f"{number}단지 필지 조회 실패: {api_response.get('status')}")
    features = (
        api_response.get("result", {})
        .get("featureCollection", {})
        .get("features", [])
    )
    if len(features) != 1 or features[0].get("properties", {}).get("pnu") != pnu:
        raise RuntimeError(f"{number}단지 필지/PNU가 불일치합니다: {pnu}")
    feature = features[0]
    if feature.get("geometry", {}).get("type") not in ("Polygon", "MultiPolygon"):
        raise RuntimeError(f"{number}단지 필지 geometry 형식이 잘못되었습니다")
    feature["properties"] = {
        "complex_number": number,
        "pnu": pnu,
        "address": feature["properties"].get("addr"),
    }
    return feature


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()
    load_env(args.env_file)
    features = []
    for number, (dong_code, lot) in LOTS.items():
        features.append(fetch_parcel(number, dong_code, lot))
        print(f"{number}단지 필지 확인")
        time.sleep(0.1)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps({"type": "FeatureCollection", "features": features},
                   ensure_ascii=False),
        encoding="utf-8",
    )
    print(f"14개 필지 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
