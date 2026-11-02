\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
  IF (SELECT count(*) FROM zone WHERE id IN ('gg-41360-099', 'gangbuk-ui', 'gangbuk-suyu')) <> 3 THEN
    RAISE EXCEPTION '덕소·우이·수유·번동 기존 권역을 확인해야 합니다';
  END IF;
  IF EXISTS (SELECT 1 FROM complex WHERE zone_id = 'gg-41360-099')
    OR EXISTS (SELECT 1 FROM apartment_trade WHERE zone_id = 'gg-41360-099')
    OR EXISTS (SELECT 1 FROM apartment_presale_trade WHERE zone_id = 'gg-41360-099') THEN
    RAISE EXCEPTION '덕소에 연결된 단지 또는 거래가 있어 단순 삭제할 수 없습니다';
  END IF;
  IF EXISTS (SELECT 1 FROM apartment_trade t WHERE t.zone_id = 'gangbuk-ui'
    AND (t.complex_id IS NULL OR NOT EXISTS
      (SELECT 1 FROM complex c WHERE c.id = t.complex_id AND c.zone_id = 'gangbuk-ui'))) THEN
    RAISE EXCEPTION '우이 거래 중 연결 단지가 없는 건을 먼저 확인해야 합니다';
  END IF;
END $$;

CREATE TEMP TABLE removal_before ON COMMIT DROP AS
SELECT
  (SELECT count(*) FROM complex WHERE zone_id = 'gangbuk-ui') AS complex_count,
  (SELECT count(*) FROM apartment_trade WHERE zone_id = 'gangbuk-ui') AS trade_count,
  (SELECT count(*) FROM apartment_presale_trade WHERE zone_id = 'gangbuk-ui') AS presale_count;

WITH merged AS (
  SELECT ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(polygon)), 3))
    ::geometry(MultiPolygon, 4326) AS polygon
  FROM zone WHERE id IN ('gangbuk-suyu', 'gangbuk-ui')
)
UPDATE zone z SET
  polygon = merged.polygon,
  center = ST_PointOnSurface(merged.polygon),
  dong_codes = ARRAY['1130510200', '1130510300', '1130510400']::varchar[]
FROM merged WHERE z.id = 'gangbuk-suyu';

UPDATE complex SET zone_id = 'gangbuk-suyu', zone_rank = NULL
WHERE zone_id = 'gangbuk-ui';
UPDATE apartment_trade SET zone_id = 'gangbuk-suyu'
WHERE zone_id = 'gangbuk-ui';
UPDATE apartment_presale_trade SET zone_id = 'gangbuk-suyu'
WHERE zone_id = 'gangbuk-ui';

DELETE FROM zone_adjacency
WHERE zone_id IN ('gg-41360-099', 'gangbuk-ui')
   OR adjacent_zone_id IN ('gg-41360-099', 'gangbuk-ui');
DELETE FROM zone_adjacency
WHERE source = 'auto_touch'
  AND (zone_id = 'gangbuk-suyu' OR adjacent_zone_id = 'gangbuk-suyu');
DELETE FROM zone WHERE id IN ('gg-41360-099', 'gangbuk-ui');

UPDATE city c SET
  polygon = s.geom,
  center = ST_PointOnSurface(s.geom)
FROM (
  SELECT city_id,
    ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(polygon)), 3))
      ::geometry(MultiPolygon, 4326) AS geom
  FROM zone WHERE city_id IN ('11305', '41360') GROUP BY city_id
) s WHERE c.id = s.city_id;

INSERT INTO zone_adjacency (zone_id, adjacent_zone_id, source)
SELECT 'gangbuk-suyu', z.id, 'auto_touch'
FROM zone p JOIN zone z ON z.id <> p.id
WHERE p.id = 'gangbuk-suyu'
  AND p.polygon && z.polygon AND ST_Touches(p.polygon, z.polygon)
UNION ALL
SELECT z.id, 'gangbuk-suyu', 'auto_touch'
FROM zone p JOIN zone z ON z.id <> p.id
WHERE p.id = 'gangbuk-suyu'
  AND p.polygon && z.polygon AND ST_Touches(p.polygon, z.polygon)
ON CONFLICT (zone_id, adjacent_zone_id) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM zone WHERE id IN ('gg-41360-099', 'gangbuk-ui')) THEN
    RAISE EXCEPTION '폐지한 권역이 남았습니다';
  END IF;
  IF EXISTS (SELECT 1 FROM complex WHERE zone_id = 'gangbuk-ui')
    OR EXISTS (SELECT 1 FROM apartment_trade WHERE zone_id = 'gangbuk-ui')
    OR EXISTS (SELECT 1 FROM apartment_presale_trade WHERE zone_id = 'gangbuk-ui') THEN
    RAISE EXCEPTION '우이 권역에 단지 또는 거래가 남았습니다';
  END IF;
  IF EXISTS (SELECT 1 FROM complex c JOIN zone z ON z.id = c.zone_id
    WHERE z.id = 'gangbuk-suyu' AND NOT ST_Covers(z.polygon, c.location)) THEN
    RAISE EXCEPTION '우이 단지의 통합 권역 포함을 확인해야 합니다';
  END IF;
  IF EXISTS (SELECT 1 FROM apartment_trade t JOIN complex c ON c.id = t.complex_id
    WHERE c.zone_id = 'gangbuk-suyu' AND t.zone_id IS DISTINCT FROM c.zone_id) THEN
    RAISE EXCEPTION '통합 권역의 단지와 거래 권역이 다릅니다';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zone WHERE id = 'gangbuk-suyu'
    AND ST_IsValid(polygon)
    AND dong_codes = ARRAY['1130510200', '1130510300', '1130510400']::varchar[]) THEN
    RAISE EXCEPTION '통합 수유·번동 경계 또는 법정동 코드가 다릅니다';
  END IF;
END $$;

SELECT 'seoul_zones', count(*) FROM zone WHERE city_id LIKE '11%';
SELECT 'gyeonggi_zones', count(*) FROM zone WHERE city_id LIKE '41%';
SELECT 'moved_complexes', complex_count FROM removal_before;
SELECT 'moved_trades', trade_count FROM removal_before;
SELECT 'moved_presales', presale_count FROM removal_before;
COMMIT;
