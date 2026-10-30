"""남양주 와부읍의 법정리 경계를 VWorld에서 수집한다.

법정리 경계는 읍면 전체를 권역으로 잘못 포함하지 않기 위한 검증 자료다.
원본 GeoJSON은 Git에서 제외한다.
"""

import argparse
import json
import os
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "data/location/boundary/gyeonggi-legal-ri-boundaries.geojson"
PARENTS = ("41360250",)


def load_env(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"환경 변수 파일이 없습니다: {path}")
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


def fetch_ris(parent: str) -> list[dict]:
    params = {
        "service": "data",
        "version": "2.0",
        "request": "GetFeature",
        "format": "json",
        "size": "100",
        "page": "1",
        "geometry": "true",
        "attribute": "true",
        "crs": "EPSG:4326",
        "data": "LT_C_ADRI_INFO",
        "attrFilter": f"li_cd:like:{parent}",
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
    with urlopen(request, timeout=60) as response:
        payload = json.load(response)
    api_response = payload.get("response", {})
    if api_response.get("status") != "OK":
        raise RuntimeError(f"{parent} 법정리 조회 실패: {api_response.get('status')}")
    features = api_response.get("result", {}).get("featureCollection", {}).get("features", [])
    if not features:
        raise ValueError(f"{parent} 법정리가 없습니다")
    for feature in features:
        p = feature.get("properties", {})
        if not str(p.get("li_cd", "")).startswith(parent):
            raise ValueError(f"{parent} 외의 법정리 코드가 반환됐습니다")
        if feature.get("geometry", {}).get("type") not in ("Polygon", "MultiPolygon"):
            raise ValueError(f"{p.get('li_cd')} geometry가 잘못됐습니다")
    return features


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    args = parser.parse_args()
    load_env(args.env_file)
    features = [feature for parent in PARENTS for feature in fetch_ris(parent)]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps({"type": "FeatureCollection", "features": features},
                   ensure_ascii=False) + "\n", encoding="utf-8",
    )
    for feature in features:
        props = feature["properties"]
        print(props["li_cd"], props["full_nm"])
    print(f"법정리 경계 {len(features)}개 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
