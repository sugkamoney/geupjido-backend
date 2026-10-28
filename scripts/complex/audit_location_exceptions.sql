-- #91 로컬 DB의 단지 위치 예외를 읽기 전용으로 재확인한다.
-- 상태가 예상과 다르면 해당 단지의 원본 주소와 실제 부지를 다시 검토한다.

WITH pending(id) AS (
    VALUES
        ('A10019943'), ('A10024245'), ('A10025753'),
        ('A10026719'), ('A10027424'), ('A12125202'),
        ('A12201301'), ('A13185607'), ('A13276415'),
        ('A13613201'), ('A15183001'), ('A15603003')
)
SELECT p.id, c.name, c.legal_dong_code,
       CASE WHEN c.location IS NULL THEN 'NO_LOCATION' ELSE 'HAS_LOCATION' END AS location_status,
       COALESCE(c.zone_id, 'UNASSIGNED') AS zone_status
FROM pending p
LEFT JOIN complex c ON c.id = p.id
ORDER BY p.id;

SELECT c.id, c.legal_dong_code, c.zone_id,
       ST_SRID(c.location) AS srid,
       ST_Covers(z.polygon, c.location) AS covered_by_zone,
       c.legal_dong_code = ANY(z.dong_codes) AS legal_dong_matches
FROM complex c
JOIN zone z ON z.id = c.zone_id
WHERE c.id IN (
    'A10025641', 'A15722001', 'A15722004', 'A15722011',
    'A15722104', 'A15722106', 'A15784601'
)
ORDER BY c.id;

SELECT count(*) AS complexes,
       count(location) AS located,
       count(zone_id) AS assigned,
       count(*) FILTER (WHERE location IS NULL) AS without_location,
       count(*) FILTER (WHERE location IS NOT NULL AND zone_id IS NULL) AS without_zone,
       count(*) FILTER (WHERE id = 'A10027875') AS busan_e2e_fixture
FROM complex;

SELECT c.id, c.address_road, c.address_jibun, c.zone_id,
       ST_SRID(c.location) AS srid,
       ST_Covers(z.polygon, c.location) AS covered_by_zone
FROM complex c
JOIN zone z ON z.id = c.zone_id
WHERE c.id IN ('A10024245', 'A13613201', 'A15183001')
ORDER BY c.id;
