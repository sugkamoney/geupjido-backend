-- 서비스 정의의 5.x 세부 급지를 저장할 수 있도록 허용 범위를 확장한다.
ALTER TABLE zone
DROP CONSTRAINT chk_zone_tier_range;

ALTER TABLE zone
    ADD CONSTRAINT chk_zone_tier_range
        CHECK (tier IS NULL OR tier BETWEEN 1.0 AND 5.9);
