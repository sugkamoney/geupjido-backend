"""자동 지오코딩이 복수 후보를 반환한 경기 단지의 현행 필지를 확인한다."""

import argparse
import json
from pathlib import Path

from download_seoul_exception_parcels import fetch_parcel, load_env


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "data/location/boundary/gyeonggi-complex-exception-parcels.geojson"
CASES = {
    "A41137104": ("주엽강선마을11단지태영", "4128710200", 73, 0),
    "A46382909": ("이매촌삼환", "4113510600", 99, 0),
    "A46382916": ("이매촌삼성", "4113510600", 100, 0),
    "A10027848": ("래미안 부천중동 아파트", "4119210800", 1293, 1),
    "A10023362": ("부천일루미스테이트", "4119410300", 327, 0),
    "A10024578": ("삼송역현대헤리엇아파트", "4128111100", 293, 0),
    "A41222002": ("행신벽산", "4128112800", 653, 38),
}


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
        json.dumps({"type": "FeatureCollection", "features": features}, ensure_ascii=False),
        encoding="utf-8",
    )
    for feature in features:
        print(feature["properties"]["complex_id"], feature["properties"]["address"])
    print(f"{len(features)}개 필지 저장: {OUTPUT}")


if __name__ == "__main__":
    main()
