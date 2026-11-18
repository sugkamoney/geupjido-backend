-- 기존 게임은 일반 월드컵으로 유지하고, 권역 안 단지 비교를 별도 흐름으로 저장한다.
ALTER TABLE worldcup_game
    ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE worldcup_game
    ADD CONSTRAINT chk_worldcup_game_mode
        CHECK (mode IN ('GENERAL', 'ZONE_COMPLEX'));

ALTER TABLE worldcup_game
    DROP CONSTRAINT chk_worldcup_game_tier_range;

ALTER TABLE worldcup_game
    ALTER COLUMN tier_range_used DROP NOT NULL;

ALTER TABLE worldcup_game
    ADD CONSTRAINT chk_worldcup_game_tier_range
        CHECK ((mode = 'GENERAL' AND tier_range_used IN (0.2, 0.3))
            OR (mode = 'ZONE_COMPLEX' AND tier_range_used IS NULL));

CREATE INDEX idx_worldcup_game_anonymous_mode_latest
    ON worldcup_game (anonymous_id, mode, base_zone_id, id DESC);
