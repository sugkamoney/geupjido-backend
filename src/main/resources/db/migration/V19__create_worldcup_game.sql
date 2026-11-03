-- 익명 방문자의 게임별 대진을 고정해 재조회해도 카드와 순서가 바뀌지 않도록 한다.
CREATE TABLE worldcup_game
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    anonymous_id    UUID          NOT NULL,
    base_zone_id    VARCHAR(50)   NOT NULL,
    tier_range_used NUMERIC(2, 1) NOT NULL,
    matchups        JSONB         NOT NULL,
    started_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_worldcup_game_tier_range
        CHECK (tier_range_used IN (0.2, 0.3)),
    CONSTRAINT chk_worldcup_game_ten_matchups
        CHECK (jsonb_typeof(matchups) = 'array' AND jsonb_array_length(matchups) = 10)
);

CREATE INDEX idx_worldcup_game_anonymous_latest
    ON worldcup_game (anonymous_id, id DESC);
