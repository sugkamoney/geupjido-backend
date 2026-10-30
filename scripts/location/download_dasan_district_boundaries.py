"""VWorld 지구단위계획 경계에서 다산진건·다산지금 지구를 확인한다.

두 지구는 같은 다산동 법정동에 걸쳐 있으므로 행정동 경계를 대용하지 않는다.
원본 GeoJSON은 Git에서 제외한다.
"""

import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from download_seoul_exception_parcels import load_env


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "data/location/boundary/dasan-district-boundaries.geojson"
LAYER = "LT_C_UPISUQ161"
DISTRICTS = {
    "다산진건": ((127.1519684, 37.6194158), "41360UQ161PS201402070135", 2712663),
    "다산지금": ((127.1690946, 37.5992842), "41360UQ161PS202210260001", 2035536),
}


def fetch_district(name: str, point: tuple[float, float], expected_id: str,
                   expected_area: int) -> dict:
    lon, lat = point
    params = {
        "service": "data", "version": "2.0", "request": "GetFeature",
        "format": "json", "size": "10", "page": "1",
        "geometry": "true", "attribute": "true", "crs": "EPSG:4326",
        "data": LAYER, "geomFilter": f"POINT({lon} {lat})",
        "key": os.environ["VWORLD_API_KEY"],
        "domain": os.environ["VWORLD_DOMAIN"],
    }
    request = Request(
        "https://api.vworld.kr/req/data?" + urlencode(params),
        headers={
            "Accept": "application/json",
            "Referer": os.environ["VWORLD_DOMAIN"],
            "User-Agent": "geupjido-location-import/1.0",
        },
    )
    with urlopen(request, timeout=30) as response:
        payload = json.load(response)
    result = payload.get("response", {})
    if result.get("status") != "OK":
        raise RuntimeError(f"{name} 경계 조회 실패: {result.get('status')}")
    features = (
        result.get("result", {}).get("featureCollection", {}).get("features", [])
    )
    if len(features) != 1:
        raise RuntimeError(f"{name} 경계 후보 수가 1개가 아닙니다: {len(features)}")
    feature = features[0]
    properties = feature.get("properties", {})
    if (properties.get("present_sn") != expected_id
            or int(properties.get("dgm_ar", -1)) != expected_area):
        raise RuntimeError(f"{name} 지구 식별자 또는 고시면적이 변경됐습니다")
    if feature.get("geometry", {}).get("type") not in ("Polygon", "MultiPolygon"):
        raise RuntimeError(f"{name} 경계 geometry 형식이 올바르지 않습니다")
    feature["properties"] = {
        "district": name,
        "layer": LAYER,
        "present_sn": expected_id,
        "dgm_ar": expected_area,
        "wtnnc_sn": properties.get("wtnnc_sn"),
        "ntfc_sn": properties.get("ntfc_sn"),
    }
    return feature


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()
    load_env(args.env_file)
    features = [
        fetch_district(name, point, district_id, area)
        for name, (point, district_id, area) in DISTRICTS.items()
    ]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps({"type": "FeatureCollection", "features": features}, ensure_ascii=False),
        encoding="utf-8",
    )
    for feature in features:
        props = feature["properties"]
        print(props["district"], props["present_sn"], props["dgm_ar"])
    print(f"{len(features)}개 공식 경계 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
