"""승인된 경기 권역의 부분 경계와 기존 단지 연결을 로컬 PostGIS에 적용한다.

일반 지역 적재기를 먼저 실행해야 한다. 기본 실행은 롤백 점검이고 --apply만 커밋한다.
다산지금은 일반 법정동 매핑에서 분리해 공식 지구단위계획 경계로 생성한다.
"""

import argparse
import csv
import json
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAPPING = ROOT / "data/location/mapping/location-mapping.json"
DECISIONS = ROOT / "data/location/mapping/gyeonggi-zone-decisions.tsv"
ADMIN = ROOT / "data/location/boundary/gyeonggi-admin-dong-boundaries-20261001.geojson"
RI = ROOT / "data/location/boundary/gyeonggi-legal-ri-boundaries.geojson"
LEGAL = ROOT / "data/location/boundary/legal-dong-boundaries.geojson"
EXCEPTION_PARCELS = ROOT / "data/location/boundary/gyeonggi-complex-exception-parcels.geojson"
DASAN_DISTRICTS = ROOT / "data/location/boundary/dasan-district-boundaries.geojson"
EXCEPTION_CASES = {
    "A41137104": ("주엽강선마을11단지태영", "4128710200", "주엽동 73", "4128710200100730000"),
    "A46382909": ("이매촌삼환", "4113510600", "이매동 99", "4113510600100990000"),
    "A46382916": ("이매촌삼성", "4113510600", "이매동 100", "4113510600101000000"),
    "A10027848": ("래미안 부천중동 아파트", "4119210800", "중동 1293-1", "4119210800112930001"),
    "A10023362": ("부천일루미스테이트", "4119410300", "범박동 327", "4119410300103270000"),
    "A10024578": ("삼송역현대헤리엇아파트", "4128111100", "삼송동 293", "4128111100102930000"),
    "A41222002": ("행신벽산", "4128112800", "행신동 653-38", "4128112800106530038"),
}
LEGAL_DONG_CORRECTIONS = {
    "A10023492": ("힐스테이트지제역퍼스티움", "경기도 평택시 지제동삭3로 14", "4122011900", "4122012000"),
    "A10024484": ("힐스테이트지제역아파트", "경기도 평택시 지제동삭1로 91", "4122011900", "4122012000"),
}
PARCEL_ZONE_EXPANSIONS = {
    "A10027848": ("41192", "중동"),
    "A10023362": ("41194", "범박"),
    "A10024578": ("41281", "삼송"),
    "A41222002": ("41281", "행신"),
}

# 법정동 전체가 아닌 생활권만 포함하는 경우 공식 행정동 경계로 교차한다.
ADMIN_CLIPS = {
    ("41450", "위례"): ("4145058500",),
    ("41450", "미사"): ("4145061000", "4145062000", "4145063000"),
    ("41173", "인덕원"): ("4117355900",),
    ("41390", "목감"): ("4139057000",),
    ("41480", "운정"): (
        "4148055000", "4148056000", "4148057000",
        "4148058000", "4148059000", "4148060000",
    ),
    ("41480", "금촌"): ("4148051000", "4148052000", "4148053000"),
}


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def geo_literal(geometry: dict) -> str:
    payload = json.dumps(geometry, ensure_ascii=False, separators=(",", ":"))
    return f"ST_SetSRID(ST_GeomFromGeoJSON($geo${payload}$geo$),4326)"


def build_sql(apply: bool) -> str:
    mapping = json.loads(MAPPING.read_text(encoding="utf-8"))
    zones = [zone for zone in mapping["zones"] if zone["id"].startswith("gg-")]
    if len(zones) != 128:
        raise ValueError(f"경기 일반 권역은 128개여야 합니다: {len(zones)}")
    by_name = {(zone["cityId"], zone["name"]): zone for zone in zones}
    with DECISIONS.open(encoding="utf-8-sig", newline="") as source:
        decision_rows = list(csv.DictReader(source, delimiter="\t"))
    special_specs = {
        ("남양주시", "다산지금"): ("41360", "4136011200", 97, 4.1),
        ("하남시", "감이"): ("41450", "4145011500", 129, 3.7),
        ("안양시", "관양"): ("41173", "4117310200", 130, 3.9),
        ("남양주시", "다산기타"): ("41360", "4136011200", 131, 4.4),
    }
    special_zones = []
    for key, (city_id, dong_code, index, tier) in special_specs.items():
        if len(decision_rows) < index or (
            decision_rows[index - 1]["시군구"],
            decision_rows[index - 1]["권역명"],
        ) != key or float(decision_rows[index - 1]["초기급지"]) != tier:
            raise ValueError(f"부분 경계 승인 권역이 일치하지 않습니다: {key}")
        special_zones.append({
            "id": f"gg-{city_id}-{index:03d}", "cityId": city_id,
            "name": key[1], "dongCodes": [dong_code], "initialTier": tier,
        })
    all_zones = zones + special_zones
    dasan_jigeum, gami, gwanyang, dasan_other = special_zones
    missing = set(ADMIN_CLIPS) - set(by_name)
    if missing:
        raise ValueError(f"부분 경계 권역을 찾지 못했습니다: {missing}")

    admin_codes = {code for codes in ADMIN_CLIPS.values() for code in codes}
    admin_features = [
        feature for feature in json.loads(ADMIN.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("adm_cd2") in admin_codes
    ]
    if {f["properties"]["adm_cd2"] for f in admin_features} != admin_codes:
        raise ValueError("경기 행정동 공식 경계가 누락됐습니다")
    ri_features = [
        feature for feature in json.loads(RI.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("li_cd") == "4136025021"
    ]
    if len(ri_features) != 1 or ri_features[0]["properties"]["li_kor_nm"] != "덕소리":
        raise ValueError("덕소리 공식 경계가 누락됐습니다")
    godeok_features = [
        feature for feature in json.loads(LEGAL.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("emd_cd") == "41220128"
    ]
    if len(godeok_features) != 1:
        raise ValueError("현행 고덕동 법정동 경계가 누락됐습니다")
    dasan_legal_features = [
        feature for feature in json.loads(LEGAL.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("emd_cd") == "41360112"
    ]
    if len(dasan_legal_features) != 1:
        raise ValueError("현행 다산동 법정동 경계가 누락됐습니다")
    gami_legal_features = [
        feature for feature in json.loads(LEGAL.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("emd_cd") == "41450115"
    ]
    gwanyang_legal_features = [
        feature for feature in json.loads(LEGAL.read_text(encoding="utf-8"))["features"]
        if feature["properties"].get("emd_cd") == "41173102"
    ]
    if len(gami_legal_features) != 1 or len(gwanyang_legal_features) != 1:
        raise ValueError("감이동·관양동 법정동 경계가 누락됐습니다")
    dasan_features = {
        feature["properties"]["district"]: feature
        for feature in json.loads(DASAN_DISTRICTS.read_text(encoding="utf-8"))["features"]
    }
    expected_dasan = {
        "다산진건": ("41360UQ161PS201402070135", 2712663),
        "다산지금": ("41360UQ161PS202210260001", 2035536),
    }
    if (set(dasan_features) != set(expected_dasan)
            or any(
                dasan_features[name]["properties"].get("present_sn") != district_id
                or dasan_features[name]["properties"].get("dgm_ar") != area
                or dasan_features[name]["properties"].get("layer") != "LT_C_UPISUQ161"
                for name, (district_id, area) in expected_dasan.items()
            )):
        raise ValueError("다산진건·다산지금 공식 경계 식별자가 다릅니다")
    exception_features = json.loads(EXCEPTION_PARCELS.read_text(encoding="utf-8"))["features"]
    if {f["properties"]["complex_id"] for f in exception_features} != set(EXCEPTION_CASES):
        raise ValueError("경기 지오코딩 예외 필지가 누락됐습니다")
    for feature in exception_features:
        complex_id = feature["properties"]["complex_id"]
        if feature["properties"]["pnu"] != EXCEPTION_CASES[complex_id][3]:
            raise ValueError(f"{complex_id} 필지 PNU가 다릅니다")

    zone_values = ",".join(f"({sql_string(z['id'])})" for z in all_zones)
    zone_details = ",".join(
        f"({sql_string(z['id'])},{sql_string(z['cityId'])},{sql_string(z['name'])},"
        f"ARRAY[{','.join(sql_string(code) for code in z['dongCodes'])}]::varchar[],"
        f"{z['initialTier']})" for z in all_zones
    )
    city_details = ",".join(
        f"({sql_string(city['id'])},{sql_string(city['name'])})"
        for city in mapping["cities"] if city["regionCode"] == "gyeonggi"
    )
    admin_values = ",".join(
        f"({sql_string(f['properties']['adm_cd2'])},{geo_literal(f['geometry'])})"
        for f in admin_features
    )
    clip_values = ",".join(
        f"({sql_string(by_name[key]['id'])},{sql_string(code)})"
        for key, codes in ADMIN_CLIPS.items() for code in codes
    )
    exception_values = ",".join(
        f"({sql_string(f['properties']['complex_id'])},{sql_string(f['properties']['pnu'])},"
        f"{geo_literal(f['geometry'])})" for f in exception_features
    )
    exception_cases = ",".join(
        f"({sql_string(complex_id)},{sql_string(name)},{sql_string(code)},"
        f"{sql_string(lot)},{sql_string(pnu)})"
        for complex_id, (name, code, lot, pnu) in EXCEPTION_CASES.items()
    )
    parcel_zone_expansions = ",".join(
        f"({sql_string(complex_id)},{sql_string(by_name[key]['id'])})"
        for complex_id, key in PARCEL_ZONE_EXPANSIONS.items()
    )
    legal_corrections = ",".join(
        f"({sql_string(complex_id)},{sql_string(name)},{sql_string(road)},"
        f"{sql_string(old_code)},{sql_string(new_code)})"
        for complex_id, (name, road, old_code, new_code)
        in LEGAL_DONG_CORRECTIONS.items()
    )
    deokso_id = by_name[("41360", "덕소")]["id"]
    dasan_jingeon_id = by_name[("41360", "다산진건")]["id"]
    dasan_jigeum_id = dasan_jigeum["id"]
    gami_id, gwanyang_id, dasan_other_id = (
        gami["id"], gwanyang["id"], dasan_other["id"]
    )
    wirye_id = by_name[("41450", "위례")]["id"]
    indeokwon_id = by_name[("41173", "인덕원")]["id"]
    godeok_id = by_name[("41220", "고덕")]["id"]
    finish = "COMMIT;" if apply else "ROLLBACK;"
    assert_candidates = """
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM complex_zone_candidates WHERE candidate_count <> 1) THEN
  RAISE EXCEPTION '경기 단지에 권역 후보 누락 또는 중복이 있습니다';
 END IF;
 IF (SELECT count(*) FROM complex_zone_candidates) <>
    (SELECT count(*) FROM complex WHERE legal_dong_code LIKE '41%') THEN
  RAISE EXCEPTION '경기 단지 후보 조회 건수가 다릅니다';
 END IF;
END $$;
""" if apply else ""
    apply_sql = f"""
UPDATE complex c SET zone_id=a.zone_id,
 zone_rank=CASE WHEN c.zone_id IS DISTINCT FROM a.zone_id THEN NULL ELSE c.zone_rank END
FROM complex_zone_candidates a WHERE c.id=a.id
 AND c.zone_id IS DISTINCT FROM a.zone_id;
UPDATE apartment_trade t SET zone_id=c.zone_id
FROM complex c WHERE t.complex_id=c.id AND c.legal_dong_code LIKE '41%'
 AND t.zone_id IS DISTINCT FROM c.zone_id;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM apartment_trade t JOIN zone z ON z.id=t.zone_id
    WHERE z.city_id LIKE '41%' AND z.id NOT IN (SELECT id FROM final_zone_id)) THEN
  RAISE EXCEPTION '기존 경기 권역에 연결된 거래가 남았습니다';
 END IF;
END $$;
DELETE FROM zone_adjacency
WHERE zone_id IN (SELECT id FROM zone WHERE city_id LIKE '41%' AND id NOT IN (SELECT id FROM final_zone_id))
 OR adjacent_zone_id IN (SELECT id FROM zone WHERE city_id LIKE '41%' AND id NOT IN (SELECT id FROM final_zone_id));
DELETE FROM zone WHERE city_id LIKE '41%' AND id NOT IN (SELECT id FROM final_zone_id);
UPDATE city c SET name=a.name,
 polygon=s.geom,center=ST_PointOnSurface(s.geom)
FROM approved_city a JOIN (
 SELECT city_id,ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(polygon)),3))
   ::geometry(MultiPolygon,4326) AS geom
 FROM zone WHERE id IN (SELECT id FROM final_zone_id) GROUP BY city_id
) s ON s.city_id=a.id WHERE c.id=a.id;
DO $$ BEGIN
 IF (SELECT count(*) FROM zone WHERE city_id LIKE '41%') <> 132 THEN
  RAISE EXCEPTION '최종 경기 권역 수가 132개가 아닙니다';
 END IF;
 IF EXISTS (SELECT 1 FROM complex c JOIN zone z ON z.id=c.zone_id
    WHERE c.legal_dong_code LIKE '41%' AND NOT ST_Covers(z.polygon,c.location)) THEN
  RAISE EXCEPTION '경기 단지 점이 배정 권역 밖에 있습니다';
 END IF;
 IF EXISTS (SELECT 1 FROM zone a JOIN zone b ON a.id < b.id
    AND a.city_id=b.city_id AND a.city_id LIKE '41%'
    AND a.polygon && b.polygon
    WHERE ST_Area(ST_Intersection(a.polygon,b.polygon)::geography) > 1) THEN
  RAISE EXCEPTION '경기 권역 경계가 1㎡ 넘게 겹칩니다';
 END IF;
 IF EXISTS (SELECT 1 FROM apartment_trade t JOIN complex c ON c.id=t.complex_id
    WHERE c.legal_dong_code LIKE '41%' AND t.zone_id IS DISTINCT FROM c.zone_id) THEN
  RAISE EXCEPTION '경기 거래와 단지의 권역이 다릅니다';
 END IF;
END $$;
SELECT 'final_gyeonggi_zones',count(*) FROM zone WHERE city_id LIKE '41%';
SELECT 'assigned_gyeonggi_complexes',count(*) FROM complex
 WHERE legal_dong_code LIKE '41%' AND zone_id IS NOT NULL;
""" if apply else ""

    return f"""
\\set ON_ERROR_STOP on
BEGIN;
CREATE TEMP TABLE final_zone_id (id varchar(50) PRIMARY KEY) ON COMMIT DROP;
INSERT INTO final_zone_id VALUES {zone_values};
CREATE TEMP TABLE approved_zone (
 id varchar(50) PRIMARY KEY, city_id varchar(10), name varchar(50),
 dong_codes varchar[], tier numeric(2,1)
) ON COMMIT DROP;
INSERT INTO approved_zone VALUES {zone_details};
CREATE TEMP TABLE approved_city (id varchar(10) PRIMARY KEY,name varchar(50)) ON COMMIT DROP;
INSERT INTO approved_city VALUES {city_details};
CREATE TEMP TABLE admin_boundary (
 code varchar(10) PRIMARY KEY, geom geometry(MultiPolygon,4326)
) ON COMMIT DROP;
INSERT INTO admin_boundary VALUES {admin_values};
CREATE TEMP TABLE admin_clip (zone_id varchar(50), admin_code varchar(10)) ON COMMIT DROP;
INSERT INTO admin_clip VALUES {clip_values};
CREATE TEMP TABLE clipped_zone ON COMMIT DROP AS
SELECT z.id,
 ST_Multi(ST_CollectionExtract(ST_Intersection(z.polygon,ST_UnaryUnion(ST_Collect(a.geom))),3))
 ::geometry(MultiPolygon,4326) geom
FROM zone z JOIN admin_clip c ON c.zone_id=z.id
 JOIN admin_boundary a ON a.code=c.admin_code GROUP BY z.id,z.polygon;
INSERT INTO clipped_zone VALUES (
 {sql_string(deokso_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Intersection(z.polygon,{geo_literal(ri_features[0]['geometry'])}),3))
  ::geometry(MultiPolygon,4326) FROM zone z WHERE z.id={sql_string(deokso_id)})
),(
 {sql_string(godeok_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Intersection(z.polygon,{geo_literal(godeok_features[0]['geometry'])}),3))
  ::geometry(MultiPolygon,4326) FROM zone z WHERE z.id={sql_string(godeok_id)})
),(
 {sql_string(dasan_jingeon_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Intersection(z.polygon,{geo_literal(dasan_features['다산진건']['geometry'])}),3))
  ::geometry(MultiPolygon,4326) FROM zone z WHERE z.id={sql_string(dasan_jingeon_id)})
),(
 {sql_string(dasan_jigeum_id)},
 ST_Multi(ST_CollectionExtract(ST_Intersection(
  {geo_literal(dasan_features['다산지금']['geometry'])},
  {geo_literal(dasan_legal_features[0]['geometry'])}),3))::geometry(MultiPolygon,4326)
);
INSERT INTO clipped_zone VALUES (
 {sql_string(gami_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Difference(
  {geo_literal(gami_legal_features[0]['geometry'])}, geom),3))
  ::geometry(MultiPolygon,4326) FROM clipped_zone WHERE id={sql_string(wirye_id)})
),(
 {sql_string(gwanyang_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Difference(
  {geo_literal(gwanyang_legal_features[0]['geometry'])}, geom),3))
  ::geometry(MultiPolygon,4326) FROM clipped_zone WHERE id={sql_string(indeokwon_id)})
),(
 {sql_string(dasan_other_id)},
 (SELECT ST_Multi(ST_CollectionExtract(ST_Difference(
  {geo_literal(dasan_legal_features[0]['geometry'])},
  (SELECT ST_UnaryUnion(ST_Collect(geom)) FROM clipped_zone
   WHERE id IN ({sql_string(dasan_jingeon_id)},{sql_string(dasan_jigeum_id)}))),3))
  ::geometry(MultiPolygon,4326))
);
CREATE TEMP TABLE exception_parcel (
 complex_id varchar(20) PRIMARY KEY, pnu varchar(19) UNIQUE,
 geom geometry(MultiPolygon,4326)
) ON COMMIT DROP;
INSERT INTO exception_parcel VALUES {exception_values};
CREATE TEMP TABLE exception_complex (
 complex_id varchar(20) PRIMARY KEY, name varchar(100),
 legal_dong_code varchar(10), lot varchar(20), pnu varchar(19)
) ON COMMIT DROP;
INSERT INTO exception_complex VALUES {exception_cases};
CREATE TEMP TABLE parcel_zone_expansion (
 complex_id varchar(20) PRIMARY KEY, zone_id varchar(50)
) ON COMMIT DROP;
INSERT INTO parcel_zone_expansion VALUES {parcel_zone_expansions};
CREATE TEMP TABLE legal_dong_correction (
 complex_id varchar(20) PRIMARY KEY, name varchar(100),
 road varchar(200), old_code varchar(10), new_code varchar(10)
) ON COMMIT DROP;
INSERT INTO legal_dong_correction VALUES {legal_corrections};
DO $$ BEGIN
 IF (SELECT count(*) FROM zone z JOIN final_zone_id f ON f.id=z.id
     WHERE z.id NOT IN ({sql_string(dasan_jigeum_id)},{sql_string(gami_id)},
                       {sql_string(gwanyang_id)},{sql_string(dasan_other_id)})) <> 128 THEN
  RAISE EXCEPTION '경기 일반 권역 적재를 먼저 실행해야 합니다';
 END IF;
 IF EXISTS (SELECT 1 FROM clipped_zone WHERE geom IS NULL OR ST_IsEmpty(geom) OR NOT ST_IsValid(geom)) THEN
  RAISE EXCEPTION '경기 부분 권역 경계가 비었거나 유효하지 않습니다';
 END IF;
 IF EXISTS (SELECT 1 FROM exception_parcel WHERE NOT ST_IsValid(geom)) THEN
  RAISE EXCEPTION '경기 예외 필지가 유효하지 않습니다';
 END IF;
 IF (SELECT count(*) FROM exception_complex e
     JOIN complex c ON c.id=e.complex_id AND c.name=e.name
      AND c.legal_dong_code=e.legal_dong_code
      AND c.address_jibun LIKE '%' || e.lot || '%'
     JOIN exception_parcel p ON p.complex_id=e.complex_id AND p.pnu=e.pnu) <>
    (SELECT count(*) FROM complex WHERE id IN (SELECT complex_id FROM exception_complex)) THEN
  RAISE EXCEPTION '경기 예외 단지 주소와 필지가 일치하지 않습니다';
 END IF;
 IF (SELECT count(*) FROM complex WHERE id IN (SELECT complex_id FROM exception_complex)) <>
    (SELECT count(*) FROM exception_complex) THEN
  RAISE EXCEPTION '경기 예외 단지가 DB에 누락됐습니다';
 END IF;
 IF (SELECT count(*) FROM legal_dong_correction a JOIN complex c
     ON c.id=a.complex_id AND c.name=a.name AND c.address_road=a.road
      AND c.legal_dong_code IN (a.old_code,a.new_code)) <>
    (SELECT count(*) FROM legal_dong_correction) THEN
  RAISE EXCEPTION '평택 법정동 보정 대상 주소·코드가 다릅니다';
 END IF;
END $$;
UPDATE zone z SET city_id=a.city_id,name=a.name,dong_codes=a.dong_codes,tier=a.tier
FROM approved_zone a WHERE z.id=a.id;
UPDATE zone z SET polygon=c.geom,center=ST_PointOnSurface(c.geom)
FROM clipped_zone c WHERE z.id=c.id;
INSERT INTO zone (id,city_id,name,dong_codes,polygon,center,tier)
SELECT a.id,a.city_id,a.name,a.dong_codes,c.geom,ST_PointOnSurface(c.geom),a.tier
FROM approved_zone a JOIN clipped_zone c ON c.id=a.id
WHERE a.id IN ({sql_string(dasan_jigeum_id)},{sql_string(gami_id)},
               {sql_string(gwanyang_id)},{sql_string(dasan_other_id)})
ON CONFLICT (id) DO UPDATE SET city_id=EXCLUDED.city_id,name=EXCLUDED.name,
 dong_codes=EXCLUDED.dong_codes,polygon=EXCLUDED.polygon,
 center=EXCLUDED.center,tier=EXCLUDED.tier;
UPDATE zone z SET polygon=a.geom,center=ST_PointOnSurface(a.geom)
FROM (
 SELECT e.zone_id,
 ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(z.polygon,p.geom)),3))
   ::geometry(MultiPolygon,4326) AS geom
 FROM parcel_zone_expansion e JOIN zone z ON z.id=e.zone_id
 JOIN exception_parcel p ON p.complex_id=e.complex_id
) a WHERE z.id=a.zone_id;
UPDATE complex c SET location=ST_PointOnSurface(p.geom)
FROM exception_parcel p WHERE c.id=p.complex_id
 AND (c.location IS NULL OR NOT ST_Covers(p.geom,c.location));
UPDATE complex c SET legal_dong_code=a.new_code
FROM legal_dong_correction a WHERE c.id=a.complex_id
 AND c.legal_dong_code=a.old_code;
DO $$ BEGIN
 IF EXISTS (
  SELECT 1 FROM complex c JOIN legal_dong_correction a ON a.complex_id=c.id
  JOIN zone z ON z.city_id=left(a.new_code,5)
   AND a.new_code=ANY(z.dong_codes) AND z.name='세교'
  WHERE c.legal_dong_code=a.new_code AND NOT ST_Covers(z.polygon,c.location)
 ) THEN
  RAISE EXCEPTION '평택 법정동 보정 단지 좌표가 세교 권역 밖에 있습니다';
 END IF;
END $$;
CREATE TEMP TABLE complex_zone_candidates ON COMMIT DROP AS
SELECT c.id,count(z.id) AS candidate_count,min(z.id) AS zone_id
FROM complex c LEFT JOIN zone z
 ON z.city_id=left(c.legal_dong_code,5)
 AND z.id IN (SELECT id FROM final_zone_id)
 AND c.legal_dong_code=ANY(z.dong_codes)
 AND ST_Covers(z.polygon,c.location)
WHERE c.legal_dong_code LIKE '41%' GROUP BY c.id;
SELECT candidate_count,count(*) FROM complex_zone_candidates
 GROUP BY candidate_count ORDER BY candidate_count;
SELECT left(c.legal_dong_code,5) AS city_id,
 CASE WHEN EXISTS (
  SELECT 1 FROM zone z WHERE z.id IN (SELECT id FROM final_zone_id)
   AND c.legal_dong_code=ANY(z.dong_codes)
 ) THEN 'MAPPED_CODE_OUTSIDE_BOUNDARY' ELSE 'OUTSIDE_APPROVED_SCOPE' END AS reason,
 count(*)
FROM complex_zone_candidates a JOIN complex c ON c.id=a.id
WHERE a.candidate_count=0 GROUP BY city_id,reason ORDER BY city_id,reason;
SELECT c.id,c.name,c.legal_dong_code,c.zone_id AS old_zone_id,
 a.candidate_count,a.zone_id AS candidate_zone_id
FROM complex_zone_candidates a JOIN complex c ON c.id=a.id
WHERE a.candidate_count<>1 ORDER BY c.id LIMIT 80;
{assert_candidates}
{apply_sql}
{finish}
"""


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    command = [
        "docker", "exec", "-i", "-u", "postgres", "geupjido-postgres",
        "psql", "-U", "postgres", "-d", "geupjido", "-At", "-F", "|",
        "-v", "ON_ERROR_STOP=1",
    ]
    result = subprocess.run(command, input=build_sql(args.apply), text=True)
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
