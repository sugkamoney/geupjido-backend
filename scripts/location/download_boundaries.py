import json
import os
import socket
import time
from pathlib import Path
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ENV_PATH = PROJECT_ROOT / ".env"
VWORLD_API_URL = "https://api.vworld.kr/req/data"
MAPPING_PATH = PROJECT_ROOT / "data/location/mapping/location-mapping.json"
OUTPUT_PATH = (
    PROJECT_ROOT
    / "data/location/boundary/legal-dong-boundaries.geojson"
)
CHECKPOINT_PATH = (
    PROJECT_ROOT
    / "data/location/boundary/legal-dong-boundaries.partial.geojson"
)
MAX_ATTEMPTS = 3
REQUEST_TIMEOUT_SECONDS = 60
CHECKPOINT_INTERVAL = 10


def load_env(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f".env 파일을 찾을 수 없습니다: {path}")

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()

        if not line or line.startswith("#"):
            continue

        key, separator, value = line.partition("=")

        if not separator:
            continue

        os.environ.setdefault(
            key.strip(),
            value.strip().strip("\"'"),
        )


def require_env(name: str) -> str:
    value = os.environ.get(name)

    if value is None or not value.strip():
        raise RuntimeError(f"필수 환경변수가 없습니다: {name}")

    return value.strip()


def load_target_emd_codes(path: Path) -> list[str]:
    mapping = json.loads(
        path.read_text(encoding="utf-8")
    )

    emd_codes = set()

    for zone in mapping.get("zones", []):
        for dong_code in zone.get("dongCodes", []):
            if (
                len(dong_code) != 10
                or not dong_code.isdigit()
                or not dong_code.endswith("00")
            ):
                raise ValueError(
                    f"잘못된 법정동 코드입니다: {dong_code}"
                )

            emd_codes.add(dong_code[:8])

    if not emd_codes:
        raise ValueError("수집할 법정동 코드가 없습니다.")

    return sorted(emd_codes)


def validate_feature(feature: dict, emd_code: str) -> None:
    properties = feature.get("properties", {})
    returned_code = str(properties.get("emd_cd", ""))

    if returned_code != emd_code:
        raise RuntimeError(
            "VWorld 응답의 법정동 코드가 요청과 다릅니다: "
            f"요청={emd_code}, 응답={returned_code}"
        )

    geometry_type = feature.get("geometry", {}).get("type")

    if geometry_type not in {"Polygon", "MultiPolygon"}:
        raise RuntimeError(
            "VWorld 응답의 경계 형식이 올바르지 않습니다: "
            f"{emd_code}, {geometry_type}"
        )


def fetch_boundary(
    api_key: str,
    domain: str,
    emd_code: str,
) -> dict:
    parameters = {
        "service": "data",
        "version": "2.0",
        "request": "GetFeature",
        "format": "json",
        "size": "1",
        "page": "1",
        "geometry": "true",
        "attribute": "true",
        "crs": "EPSG:4326",
        "data": "LT_C_ADEMD_INFO",
        "attrFilter": f"emd_cd:=:{emd_code}",
        "key": api_key,
        "domain": domain,
    }

    request_url = (
        f"{VWORLD_API_URL}?{urlencode(parameters)}"
    )

    request = Request(
        request_url,
        headers={
            "Accept": "application/json",
            "Referer": domain,
            "User-Agent": "geupjido-location-import/1.0",
        },
    )

    with urlopen(
        request,
        timeout=REQUEST_TIMEOUT_SECONDS,
    ) as response:
        payload = json.load(response)

    api_response = payload.get("response", {})

    if api_response.get("status") != "OK":
        error = api_response.get("error", {})
        message = error.get("text", "알 수 없는 오류")

        raise RuntimeError(
            f"VWorld API 호출에 실패했습니다: {message}"
        )

    features = (
        api_response
        .get("result", {})
        .get("featureCollection", {})
        .get("features", [])
    )

    if len(features) != 1:
        raise RuntimeError(
            f"법정동 경계 조회 결과가 1개가 아닙니다: {emd_code}"
        )

    feature = features[0]
    validate_feature(feature, emd_code)

    return feature


def fetch_boundary_with_retry(
    api_key: str,
    domain: str,
    emd_code: str,
) -> dict:
    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            return fetch_boundary(
                api_key,
                domain,
                emd_code,
            )
        except (
            URLError,
            TimeoutError,
            socket.timeout,
            json.JSONDecodeError,
        ) as exception:
            if attempt == MAX_ATTEMPTS:
                raise RuntimeError(
                    "VWorld 경계 조회가 반복해서 실패했습니다: "
                    f"{emd_code}"
                ) from exception

            wait_seconds = attempt * 2
            print(
                f"{emd_code} 조회 실패, "
                f"{wait_seconds}초 후 재시도 "
                f"({attempt}/{MAX_ATTEMPTS})"
            )
            time.sleep(wait_seconds)

    raise RuntimeError(f"VWorld 경계를 조회할 수 없습니다: {emd_code}")


def write_feature_collection(
    path: Path,
    features_by_code: dict[str, dict],
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    feature_collection = {
        "type": "FeatureCollection",
        "features": [
            features_by_code[code]
            for code in sorted(features_by_code)
        ],
    }

    temporary_path = path.with_suffix(path.suffix + ".tmp")
    temporary_path.write_text(
        json.dumps(
            feature_collection,
            ensure_ascii=False,
            separators=(",", ":"),
        ) + "\n",
        encoding="utf-8",
    )
    temporary_path.replace(path)


def load_checkpoint(
    path: Path,
    target_codes: set[str],
) -> dict[str, dict]:
    if not path.exists():
        return {}

    payload = json.loads(path.read_text(encoding="utf-8"))

    if payload.get("type") != "FeatureCollection":
        raise ValueError("중간 저장 파일이 FeatureCollection이 아닙니다.")

    features_by_code = {}

    for feature in payload.get("features", []):
        emd_code = str(
            feature.get("properties", {}).get("emd_cd", "")
        )

        if emd_code not in target_codes:
            continue

        validate_feature(feature, emd_code)

        if emd_code in features_by_code:
            raise ValueError(
                f"중간 저장 파일에 중복 코드가 있습니다: {emd_code}"
            )

        features_by_code[emd_code] = feature

    return features_by_code


def download_boundaries(
    api_key: str,
    domain: str,
    emd_codes: list[str],
) -> dict[str, dict]:
    target_codes = set(emd_codes)
    features_by_code = load_checkpoint(
        CHECKPOINT_PATH,
        target_codes,
    )

    if features_by_code:
        print(f"중간 저장 데이터 {len(features_by_code)}개를 이어받습니다.")

    try:
        for emd_code in emd_codes:
            if emd_code in features_by_code:
                continue

            feature = fetch_boundary_with_retry(
                api_key,
                domain,
                emd_code,
            )
            features_by_code[emd_code] = feature

            completed = len(features_by_code)
            print(f"[{completed}/{len(emd_codes)}] {emd_code} 수집 완료")

            if completed % CHECKPOINT_INTERVAL == 0:
                write_feature_collection(
                    CHECKPOINT_PATH,
                    features_by_code,
                )
    except Exception:
        if features_by_code:
            write_feature_collection(
                CHECKPOINT_PATH,
                features_by_code,
            )
        raise

    return features_by_code


def main() -> None:
    load_env(ENV_PATH)

    api_key = require_env("VWORLD_API_KEY")
    domain = require_env("VWORLD_DOMAIN")
    emd_codes = load_target_emd_codes(MAPPING_PATH)

    print(f"수집 대상 법정동: {len(emd_codes)}개")

    features_by_code = download_boundaries(
        api_key,
        domain,
        emd_codes,
    )

    if len(features_by_code) != len(emd_codes):
        missing_codes = sorted(
            set(emd_codes) - set(features_by_code)
        )
        raise RuntimeError(
            "일부 법정동 경계를 수집하지 못했습니다: "
            + ", ".join(missing_codes)
        )

    write_feature_collection(
        OUTPUT_PATH,
        features_by_code,
    )

    if CHECKPOINT_PATH.exists():
        CHECKPOINT_PATH.unlink()

    print(f"GeoJSON 저장 완료: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
