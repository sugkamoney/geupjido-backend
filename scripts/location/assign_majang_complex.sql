-- #91: 주소와 법정동 코드를 확인한 마장한영스테이를 새 마장 권역에 배정한다.
-- 로컬 PostgreSQL·PostGIS에서만 수동 실행한다. 재실행해도 같은 값이 유지된다.
BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM complex c
        JOIN zone z ON z.id = 'seongdong-majang'
        WHERE c.id = 'A10020204'
          AND c.legal_dong_code = '1120010500'
          AND c.location IS NOT NULL
          AND ST_SRID(c.location) = 4326
          AND ST_Contains(z.polygon, c.location)
          AND (c.zone_id IS NULL OR c.zone_id = z.id)
    ) THEN
        RAISE EXCEPTION '마장한영스테이의 법정동·좌표·기존 권역을 확인할 수 없습니다';
    END IF;
END $$;

UPDATE complex
SET zone_id = 'seongdong-majang'
WHERE id = 'A10020204'
  AND zone_id IS DISTINCT FROM 'seongdong-majang';

COMMIT;
