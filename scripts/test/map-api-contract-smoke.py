#!/usr/bin/env python3
"""실행 중인 지도 API와 생성 OpenAPI를 읽기 전용 HTTP로 대조한다."""

import argparse
import json
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import urlopen


def request(base_url, path, expected_status=200):
    try:
        with urlopen(base_url + path, timeout=20) as response:
            status = response.status
            body = json.load(response)
    except HTTPError as error:
        status = error.code
        body = json.load(error)
    if status != expected_status:
        raise AssertionError(f"{path}: HTTP {status}, expected {expected_status}")
    return body


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def get_schema(docs, name):
    return docs["components"]["schemas"][name]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("base_url", help="예: http://127.0.0.1:18080")
    args = parser.parse_args()
    base_url = args.base_url.rstrip("/")

    health = request(base_url, "/actuator/health/liveness")
    require(health["status"] == "UP", "애플리케이션 liveness가 UP이 아닙니다")

    bounds = urlencode({"west": 124, "south": 33, "east": 132, "north": 39})
    zones = request(base_url, f"/api/v1/map/zones?{bounds}")
    require(zones, "권역 목록이 비었습니다")
    expected_pins = sum(item["complexCount"] for item in zones)
    all_pins = request(base_url, f"/api/v1/map/complexes?{bounds}")
    require(len(all_pins) == expected_pins,
            f"권역 단지 {expected_pins}개 중 지도 핀은 {len(all_pins)}개입니다")
    require(len({item["id"] for item in all_pins}) == len(all_pins),
            "지도 핀에 중복 단지 ID가 있습니다")
    zone = zones[0]
    zone_id = zone["id"]
    require(zone["areaSquareMeters"] > 0, "권역 면적이 양수가 아닙니다")

    detail = request(base_url, f"/api/v1/map/zones/{quote(zone_id)}")
    require(detail["zone"]["areaSquareMeters"] == zone["areaSquareMeters"],
            "목록·상세의 권역 면적이 다릅니다")
    polygon = detail["polygon"]
    require(polygon["type"] == "MultiPolygon" and polygon["coordinates"],
            "상세 경계가 MultiPolygon이 아닙니다")
    search = request(base_url, "/api/v1/map/search?" + urlencode({"q": zone["name"]}))
    searched_zone = next((item for item in search["zones"] if item["id"] == zone_id), None)
    require(searched_zone is not None, "권역 검색에서 대상 권역이 없습니다")
    require(searched_zone["areaSquareMeters"] == zone["areaSquareMeters"],
            "목록·검색의 권역 면적이 다릅니다")

    matched_complex = None
    for complex_item in detail["complexes"]:
        location = complex_item["location"]
        if location is None:
            continue
        complex_search = request(
            base_url, "/api/v1/map/search?" + urlencode({"q": complex_item["name"]})
        )
        searched_complex = next(
            (item for item in complex_search["complexes"]
             if item["id"] == complex_item["id"]), None
        )
        if searched_complex is None:
            continue
        require(searched_complex["location"] == location,
                "상세·검색의 단지 좌표가 다릅니다")
        longitude, latitude = location["longitude"], location["latitude"]
        pin_bounds = urlencode({
            "west": longitude - 0.001, "south": latitude - 0.001,
            "east": longitude + 0.001, "north": latitude + 0.001,
        })
        pins = request(base_url, f"/api/v1/map/complexes?{pin_bounds}")
        pin = next((item for item in pins if item["id"] == complex_item["id"]), None)
        require(pin is not None and pin["location"] == location,
                "상세·핀의 단지 좌표가 다릅니다")
        matched_complex = complex_item["id"]
        break
    require(matched_complex is not None, "상세·핀·검색을 대조할 단지가 없습니다")

    docs = request(base_url, "/v3/api-docs")
    map_zone = get_schema(docs, "MapZone")
    map_complex = get_schema(docs, "MapComplex")
    ranking_zone = get_schema(docs, "RankingZone")
    require("areaSquareMeters" in map_zone["properties"], "MapZone 면적 명세 없음")
    require("rank" in ranking_zone["properties"], "RankingZone 명세 없음")
    require("location" in map_complex["required"], "location 필수 필드 명세 없음")
    nullable_location = map_complex["properties"]["location"]["oneOf"]
    require(nullable_location[0].get("$ref") == "#/components/schemas/Point"
            and "null" in nullable_location[1].get("type", []),
            "location의 Point 또는 null 명세가 잘못됐습니다")
    require(get_schema(docs, "GeoJsonMultiPolygon")["properties"]["type"]["enum"]
            == ["MultiPolygon"], "경계 유형 명세가 잘못됐습니다")
    map_zone_ref = docs["paths"]["/api/v1/map/zones"]["get"]["responses"]["200"]
    map_zone_ref = map_zone_ref["content"]["application/json"]["schema"]["items"]["$ref"]
    require(map_zone_ref == "#/components/schemas/MapZone",
            "지도 권역이 랭킹 스키마를 참조합니다")
    search_parameter = docs["paths"]["/api/v1/map/search"]["get"]["parameters"][0]
    require(search_parameter["schema"].get("minLength") == 1
            and search_parameter["schema"].get("maxLength") == 80,
            "검색어 길이 명세가 잘못됐습니다")

    invalid = request(base_url, "/api/v1/map/search?" + urlencode({"q": "가" * 81}), 400)
    require(set(invalid) == {"code", "message"} and invalid["code"] == "INVALID_REQUEST",
            "검색 오류 응답이 잘못됐습니다")
    missing = request(base_url, "/api/v1/map/zones/unknown-zone", 404)
    require(set(missing) == {"code", "message"} and missing["code"] == "ZONE_NOT_FOUND",
            "권역 없음 응답이 잘못됐습니다")

    print(f"PASS: {len(zones)}개 권역, {expected_pins}개 단지 핀, 좌표 누락 0개")
    print(f"PASS: {zone_id} 면적·경계, 단지 {matched_complex} 상세·검색·핀 좌표")
    print("PASS: 지도·랭킹 OpenAPI 분리, nullable 위치, 검색 제한, 400·404 오류")


if __name__ == "__main__":
    main()
