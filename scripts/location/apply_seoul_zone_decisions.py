"""서울 권역의 이촌·목동 특수 경계와 단지 배정을 로컬 PostGIS에 적용한다.

먼저 일반 지역 적재기를 새 location-mapping.json과 전체 경계로 실행해야 한다.
기본 실행은 트랜잭션을 롤백하는 점검이며 --apply에서만 커밋한다.
"""

import argparse
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BOUNDARY = ROOT / "data/location/boundary/legal-dong-boundaries.geojson"
ADMIN = ROOT / "data/location/boundary/seoul-admin-dong-boundaries-20261001.geojson"
PARCELS = ROOT / "data/location/boundary/mokdong-sinjigaji-parcels.geojson"
EXCEPTION_PARCELS = ROOT / "data/location/boundary/seoul-complex-exception-parcels.geojson"
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
CANONICAL = {
    1: ("A15875101", "1147010200", 901),
    2: ("A15875102", "1147010200", 902),
    3: ("A15805003", "1147010200", 903),
    4: ("A15805503", "1147010200", 904),
    5: ("A15805504", "1147010200", 912),
    6: ("A15875103", "1147010200", 911),
    7: ("A15805115", "1147010200", 925),
    8: ("A15807604", "1147010100", 314),
    9: ("A15807101", "1147010100", 312),
    10: ("A15873701", "1147010100", 311),
    11: ("A15807705", "1147010100", 325),
    12: ("A15807706", "1147010100", 326),
    13: ("A15807605", "1147010100", 327),
    14: ("A15807606", "1147010100", 329),
}
EXCEPTION_CASES = {
    "A12174601": ("메세나폴리스", "1144012000", "490", "1144012000104900000"),
    "A14074102": ("효창한신", "1117011900", "5-145", "1117011900100050145"),
}


def geo_literal(geometry: dict) -> str:
    payload = json.dumps(geometry, ensure_ascii=False, separators=(",", ":"))
    return f"ST_SetSRID(ST_GeomFromGeoJSON($geo${payload}$geo$),4326)"


def load_boundaries() -> tuple[dict, list[dict], list[dict], list[dict]]:
    legal = json.loads(BOUNDARY.read_text(encoding="utf-8"))
    ichon = [
        item for item in legal["features"]
        if item["properties"].get("emd_cd") == "11170129"
    ]
    if len(ichon) != 1:
        raise ValueError("이촌동 법정동 경계가 하나여야 합니다")
    admin = json.loads(ADMIN.read_text(encoding="utf-8"))
    ichon_admin = [
        item for item in admin["features"]
        if item["properties"].get("adm_cd2") in
        ("1117063000", "1117064000")
    ]
    if {item["properties"]["adm_cd2"] for item in ichon_admin} != {
        "1117063000", "1117064000"
    }:
        raise ValueError("이촌1·2동 행정동 경계가 모두 필요합니다")
    parcels = json.loads(PARCELS.read_text(encoding="utf-8"))["features"]
    if {f["properties"]["complex_number"] for f in parcels} != set(range(1, 15)):
        raise ValueError("목동신시가지 1~14단지 필지가 모두 필요합니다")
    for parcel in parcels:
        number = parcel["properties"]["complex_number"]
        complex_id, dong_code, lot = CANONICAL[number]
        expected_pnu = f"{dong_code}1{lot:04d}0000"
        if parcel["properties"]["pnu"] != expected_pnu:
            raise ValueError(f"{number}단지 필지 PNU가 다릅니다")
    exception_parcels = json.loads(EXCEPTION_PARCELS.read_text(encoding="utf-8"))["features"]
    if {f["properties"]["complex_id"] for f in exception_parcels} != set(EXCEPTION_CASES):
        raise ValueError("서울 지오코딩 예외 단지 필지가 모두 필요합니다")
    for parcel in exception_parcels:
        complex_id = parcel["properties"]["complex_id"]
        _, _, _, expected_pnu = EXCEPTION_CASES[complex_id]
        if parcel["properties"]["pnu"] != expected_pnu:
            raise ValueError(f"{complex_id} 필지 PNU가 다릅니다")
    return ichon[0], ichon_admin, parcels, exception_parcels


def build_sql(apply: bool) -> str:
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    zones = [zone for zone in mapping["zones"] if zone["cityId"].startswith("11")]
    if len(zones) != 148:
        raise ValueError(f"서울 지리 권역 수가 예상과 다릅니다: {len(zones)}")
    ichon, admin, parcels, exception_parcels = load_boundaries()
    ids = [zone["id"] for zone in zones]
    ids.extend(("yongsan-ichon-west", "yangcheon-mokdong-sinjigaji"))
    id_values = ",".join(f"('{zone_id}')" for zone_id in ids)
    tier_values = ",".join(
        f"('{zone['id']}',{zone['initialTier']})" for zone in zones
    )
    admin_values = ",".join(
        f"('{item['properties']['adm_cd2']}',{geo_literal(item['geometry'])})"
        for item in admin
    )
    parcel_values = ",".join(
        f"({item['properties']['complex_number']},"
        f"'{item['properties']['pnu']}',{geo_literal(item['geometry'])})"
        for item in parcels
    )
    canonical_values = ",".join(
        f"({number},'{complex_id}','{dong_code}',{lot})"
        for number, (complex_id, dong_code, lot) in CANONICAL.items()
    )
    exception_values = ",".join(
        f"('{item['properties']['complex_id']}',"
        f"'{item['properties']['pnu']}',{geo_literal(item['geometry'])})"
        for item in exception_parcels
    )
    exception_expected_values = ",".join(
        f"('{complex_id}','{name}','{dong_code}','{lot}','{pnu}')"
        for complex_id, (name, dong_code, lot, pnu) in EXCEPTION_CASES.items()
    )
    legal_geom = geo_literal(ichon["geometry"])
    finish = "COMMIT;" if apply else "ROLLBACK;"
    assertion = """
DO $$
BEGIN
 IF (SELECT count(*) FROM complex_zone_candidates WHERE candidate_count <> 1) > 0 THEN
  RAISE EXCEPTION '서울 단지의 권역 후보가 0개 또는 여러 개입니다';
 END IF;
 IF (SELECT count(*) FROM complex_zone_candidates) <>
    (SELECT count(*) FROM complex WHERE legal_dong_code LIKE '11%') THEN
  RAISE EXCEPTION '서울 단지 후보 조회 건수가 다릅니다';
 END IF;
END $$;
""" if apply else ""
    return f"""
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE final_zone_id (id varchar(50) PRIMARY KEY) ON COMMIT DROP;
INSERT INTO final_zone_id VALUES {id_values};
CREATE TEMP TABLE zone_tier (id varchar(50) PRIMARY KEY, tier numeric(2,1)) ON COMMIT DROP;
INSERT INTO zone_tier VALUES {tier_values};
CREATE TEMP TABLE ichon_admin (code varchar(10) PRIMARY KEY, geom geometry(MultiPolygon,4326)) ON COMMIT DROP;
INSERT INTO ichon_admin VALUES {admin_values};
CREATE TEMP TABLE mokdong_parcel (
 number integer PRIMARY KEY, pnu varchar(19) UNIQUE, geom geometry(MultiPolygon,4326)
) ON COMMIT DROP;
INSERT INTO mokdong_parcel VALUES {parcel_values};
CREATE TEMP TABLE canonical_complex (
 number integer PRIMARY KEY, complex_id varchar(20) UNIQUE,
 dong_code varchar(10), lot integer
) ON COMMIT DROP;
INSERT INTO canonical_complex VALUES {canonical_values};
CREATE TEMP TABLE exception_parcel (
 complex_id varchar(20) PRIMARY KEY, pnu varchar(19) UNIQUE,
 geom geometry(MultiPolygon,4326)
) ON COMMIT DROP;
INSERT INTO exception_parcel VALUES {exception_values};
CREATE TEMP TABLE exception_expected (
 complex_id varchar(20) PRIMARY KEY, name varchar(200),
 dong_code varchar(10), lot varchar(20), pnu varchar(19)
) ON COMMIT DROP;
INSERT INTO exception_expected VALUES {exception_expected_values};

DO $$
BEGIN
 IF (SELECT count(*) FROM zone z JOIN zone_tier t ON z.id=t.id) <> 148 THEN
  RAISE EXCEPTION '일반 지역 적재를 먼저 실행해야 합니다: 서울 권역 148개 필요';
 END IF;
 IF (SELECT count(*) FROM canonical_complex a JOIN complex c ON a.complex_id=c.id
     WHERE c.legal_dong_code=a.dong_code
       AND c.address_jibun LIKE '%' || a.lot || '%') <> 14 THEN
  RAISE EXCEPTION '목동신시가지 14개 단지의 ID·주소·법정동을 확인해야 합니다';
 END IF;
 IF EXISTS (SELECT 1 FROM mokdong_parcel WHERE NOT ST_IsValid(geom)) THEN
  RAISE EXCEPTION '목동 필지 경계가 유효하지 않습니다';
 END IF;
 IF EXISTS (SELECT 1 FROM exception_parcel WHERE NOT ST_IsValid(geom)) THEN
  RAISE EXCEPTION '지오코딩 예외 필지 경계가 유효하지 않습니다';
 END IF;
 IF (SELECT count(*) FROM exception_expected e
     JOIN exception_parcel p ON p.complex_id=e.complex_id AND p.pnu=e.pnu
     JOIN complex c ON c.id=e.complex_id
     WHERE c.name=e.name AND c.legal_dong_code=e.dong_code
       AND c.address_jibun LIKE '%' || e.lot || '%') <> 2 THEN
  RAISE EXCEPTION '지오코딩 예외 단지의 ID·주소·법정동을 확인해야 합니다';
 END IF;
END $$;

-- 법정동 이촌동을 공식 행정동 1·2동 경계와 교차시켜 분리한다.
CREATE TEMP TABLE ichon_legal (geom geometry(MultiPolygon,4326)) ON COMMIT DROP;
INSERT INTO ichon_legal VALUES ({legal_geom});
CREATE TEMP TABLE ichon_split ON COMMIT DROP AS
SELECT code,
 ST_Multi(ST_CollectionExtract(ST_Intersection(l.geom,a.geom),3))::geometry(MultiPolygon,4326) geom
FROM ichon_admin a CROSS JOIN ichon_legal l;
DO $$
BEGIN
 IF (SELECT count(*) FROM ichon_split WHERE ST_IsEmpty(geom)) <> 0 THEN
  RAISE EXCEPTION '이촌 행정동과 법정동 경계가 교차하지 않습니다';
 END IF;
END $$;
UPDATE zone z
SET name='동부이촌',polygon=s.geom,center=ST_PointOnSurface(s.geom),tier=1.7
FROM ichon_split s WHERE z.id='yongsan-ichon' AND s.code='1117063000';
INSERT INTO zone(id,city_id,name,dong_codes,polygon,center,tier)
SELECT 'yongsan-ichon-west','11170','서부이촌',
 ARRAY['1117012900']::varchar[],s.geom,ST_PointOnSurface(s.geom),2.2
FROM ichon_split s WHERE s.code='1117064000'
ON CONFLICT(id) DO UPDATE SET
 name=EXCLUDED.name,dong_codes=EXCLUDED.dong_codes,
 polygon=EXCLUDED.polygon,center=EXCLUDED.center,tier=EXCLUDED.tier;

-- 이름과 주소로 검증한 신시가지 필지를 하나의 공간 권역으로 만든다.
CREATE TEMP TABLE mokdong_special ON COMMIT DROP AS
SELECT ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(geom)),3))
       ::geometry(MultiPolygon,4326) geom
FROM mokdong_parcel;
INSERT INTO zone(id,city_id,name,dong_codes,polygon,center,tier)
SELECT 'yangcheon-mokdong-sinjigaji','11470','목동(단지)',
 ARRAY['1147010100','1147010200']::varchar[],
 s.geom,ST_PointOnSurface(s.geom),2.0
FROM mokdong_special s
ON CONFLICT(id) DO UPDATE SET
 name=EXCLUDED.name,dong_codes=EXCLUDED.dong_codes,
 polygon=EXCLUDED.polygon,center=EXCLUDED.center,tier=EXCLUDED.tier;
UPDATE zone z SET
 polygon=ST_Multi(ST_CollectionExtract(ST_Difference(z.polygon,s.geom),3)),
 center=ST_PointOnSurface(
   ST_Multi(ST_CollectionExtract(ST_Difference(z.polygon,s.geom),3)))
FROM mokdong_special s
WHERE z.id IN ('yangcheon-mokdong','yangcheon-sinjeong');

-- 현재 지도 점이 주소상 필지 밖인 단지는 그 필지 내부의 대표점으로 보정한다.
UPDATE complex c SET location=ST_PointOnSurface(p.geom)
FROM canonical_complex a JOIN mokdong_parcel p ON p.number=a.number
WHERE c.id=a.complex_id AND NOT ST_Covers(p.geom,c.location);

-- 도로명 검색 결과가 복수인 단지와 경계에서 2m 벗어난 점은 공식 필지 안의 점으로 보정한다.
UPDATE complex c SET location=ST_PointOnSurface(p.geom)
FROM exception_parcel p WHERE c.id=p.complex_id
 AND (c.location IS NULL OR NOT ST_Covers(p.geom,c.location));

UPDATE zone z SET tier=t.tier FROM zone_tier t WHERE z.id=t.id;

CREATE TEMP TABLE complex_zone_candidates ON COMMIT DROP AS
SELECT c.id,
 count(z.id) AS candidate_count,
 min(z.id) AS zone_id
FROM complex c LEFT JOIN zone z
 ON z.city_id=left(c.legal_dong_code,5)
 AND z.id IN (SELECT id FROM final_zone_id)
 AND ST_Covers(z.polygon,c.location)
WHERE c.legal_dong_code LIKE '11%'
GROUP BY c.id;
SELECT candidate_count,count(*) FROM complex_zone_candidates
GROUP BY candidate_count ORDER BY candidate_count;
SELECT c.id,c.name,c.legal_dong_code,c.zone_id AS old_zone_id,
       a.candidate_count,a.zone_id AS candidate_zone_id
FROM complex_zone_candidates a JOIN complex c ON c.id=a.id
WHERE a.candidate_count<>1 ORDER BY c.id LIMIT 100;
{assertion}
""" + (f"""
UPDATE complex c SET zone_id=a.zone_id,
 zone_rank=CASE WHEN c.zone_id IS DISTINCT FROM a.zone_id THEN NULL ELSE c.zone_rank END
FROM complex_zone_candidates a
WHERE c.id=a.id AND c.zone_id IS DISTINCT FROM a.zone_id;
UPDATE apartment_trade t SET zone_id=c.zone_id
FROM complex c
WHERE t.complex_id=c.id AND c.legal_dong_code LIKE '11%'
 AND t.zone_id IS DISTINCT FROM c.zone_id;
DELETE FROM zone_adjacency
WHERE zone_id IN (SELECT id FROM zone WHERE city_id LIKE '11%' AND id NOT IN (SELECT id FROM final_zone_id))
   OR adjacent_zone_id IN (SELECT id FROM zone WHERE city_id LIKE '11%' AND id NOT IN (SELECT id FROM final_zone_id));
DELETE FROM zone WHERE city_id LIKE '11%'
 AND id NOT IN (SELECT id FROM final_zone_id);
DO $$
BEGIN
 IF (SELECT count(*) FROM zone WHERE city_id LIKE '11%') <> 150 THEN
  RAISE EXCEPTION '최종 서울 권역 수가 150개가 아닙니다';
 END IF;
 IF EXISTS (
  SELECT 1 FROM complex c JOIN zone z ON c.zone_id=z.id
  WHERE c.legal_dong_code LIKE '11%' AND NOT ST_Covers(z.polygon,c.location)
 ) THEN
  RAISE EXCEPTION '단지 점이 배정 권역 경계 밖에 있습니다';
 END IF;
 IF EXISTS (
  SELECT 1 FROM apartment_trade t JOIN complex c ON t.complex_id=c.id
  WHERE c.legal_dong_code LIKE '11%' AND t.zone_id IS DISTINCT FROM c.zone_id
 ) THEN
  RAISE EXCEPTION '거래의 권역과 단지 권역이 다릅니다';
 END IF;
END $$;
SELECT 'final_seoul_zones',count(*) FROM zone WHERE city_id LIKE '11%';
SELECT 'assigned_seoul_complexes',count(*) FROM complex WHERE legal_dong_code LIKE '11%';
SELECT 'ichon_east',count(*) FROM complex WHERE zone_id='yongsan-ichon';
SELECT 'ichon_west',count(*) FROM complex WHERE zone_id='yongsan-ichon-west';
SELECT 'mokdong_sinjigaji',count(*) FROM complex WHERE zone_id='yangcheon-mokdong-sinjigaji';
{finish}
""" if apply else "ROLLBACK;\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    sql = build_sql(args.apply)
    command = [
        "docker", "exec", "-i", "-u", "postgres", "geupjido-postgres",
        "psql", "-U", "postgres", "-d", "geupjido", "-At", "-F", "|",
        "-v", "ON_ERROR_STOP=1",
    ]
    result = subprocess.run(command, input=sql, text=True)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
