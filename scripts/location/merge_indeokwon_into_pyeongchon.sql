\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
  IF (SELECT count(*) FROM zone WHERE id IN ('gg-41173-054', 'gg-41173-055')) <> 2 THEN
    RAISE EXCEPTION '평촌·인덕원 기존 권역이 모두 있어야 합니다';
  END IF;
  IF (SELECT count(*) FROM zone WHERE id = 'gg-41173-054' AND tier = 3.5) <> 1 THEN
    RAISE EXCEPTION '평촌 급지가 예상한 3.5와 다릅니다';
  END IF;
END $$;

CREATE TEMP TABLE merge_before ON COMMIT DROP AS
SELECT
  (SELECT count(*) FROM complex WHERE zone_id = 'gg-41173-055') AS complex_count,
  (SELECT count(*) FROM apartment_trade WHERE zone_id = 'gg-41173-055') AS trade_count,
  (SELECT count(*) FROM apartment_presale_trade WHERE zone_id = 'gg-41173-055') AS presale_count;

WITH merged AS (
  SELECT ST_Multi(ST_CollectionExtract(ST_UnaryUnion(ST_Collect(polygon)), 3))
    ::geometry(MultiPolygon, 4326) AS polygon
  FROM zone WHERE id IN ('gg-41173-054', 'gg-41173-055')
)
UPDATE zone z SET
  polygon = merged.polygon,
  center = ST_PointOnSurface(merged.polygon),
  dong_codes = ARRAY['4117310300', '4117310200']::varchar[]
FROM merged WHERE z.id = 'gg-41173-054';

UPDATE complex SET zone_id = 'gg-41173-054', zone_rank = NULL
WHERE zone_id = 'gg-41173-055';
UPDATE apartment_trade SET zone_id = 'gg-41173-054'
WHERE zone_id = 'gg-41173-055';
UPDATE apartment_presale_trade SET zone_id = 'gg-41173-054'
WHERE zone_id = 'gg-41173-055';

DELETE FROM zone_adjacency
WHERE zone_id = 'gg-41173-055' OR adjacent_zone_id = 'gg-41173-055';
DELETE FROM zone_adjacency
WHERE source = 'auto_touch'
  AND (zone_id = 'gg-41173-054' OR adjacent_zone_id = 'gg-41173-054');
DELETE FROM zone WHERE id = 'gg-41173-055';

INSERT INTO zone_adjacency (zone_id, adjacent_zone_id, source)
SELECT 'gg-41173-054', z.id, 'auto_touch'
FROM zone p JOIN zone z ON z.id <> p.id
WHERE p.id = 'gg-41173-054'
  AND p.polygon && z.polygon AND ST_Touches(p.polygon, z.polygon)
UNION ALL
SELECT z.id, 'gg-41173-054', 'auto_touch'
FROM zone p JOIN zone z ON z.id <> p.id
WHERE p.id = 'gg-41173-054'
  AND p.polygon && z.polygon AND ST_Touches(p.polygon, z.polygon)
ON CONFLICT (zone_id, adjacent_zone_id) DO NOTHING;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM zone WHERE id = 'gg-41173-055') THEN
    RAISE EXCEPTION '인덕원 권역이 남았습니다';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM zone WHERE id = 'gg-41173-054'
    AND ST_IsValid(polygon) AND tier = 3.5
    AND dong_codes = ARRAY['4117310300', '4117310200']::varchar[]) THEN
    RAISE EXCEPTION '통합 평촌 경계·급지가 올바르지 않습니다';
  END IF;
  IF EXISTS (SELECT 1 FROM complex c JOIN zone z ON z.id = c.zone_id
    WHERE z.id = 'gg-41173-054' AND NOT ST_Covers(z.polygon, c.location)) THEN
    RAISE EXCEPTION '통합 평촌 권역 밖에 배정된 단지가 있습니다';
  END IF;
  IF EXISTS (SELECT 1 FROM apartment_trade t JOIN complex c ON c.id = t.complex_id
    WHERE c.zone_id = 'gg-41173-054' AND t.zone_id IS DISTINCT FROM c.zone_id) THEN
    RAISE EXCEPTION '통합 평촌 단지와 거래의 권역이 다릅니다';
  END IF;
  IF (SELECT count(*) FROM complex WHERE zone_id = 'gg-41173-054') <
    (SELECT complex_count FROM merge_before) THEN
    RAISE EXCEPTION '인덕원 단지 이동이 누락됐습니다';
  END IF;
END $$;

SELECT 'merged_zone_count', count(*) FROM zone WHERE city_id = '41173';
SELECT 'merged_complex_count', count(*) FROM complex WHERE zone_id = 'gg-41173-054';
SELECT 'merged_active_count', count(*) FROM complex
WHERE zone_id = 'gg-41173-054' AND is_active;
SELECT 'moved_trade_count', trade_count FROM merge_before;
COMMIT;
