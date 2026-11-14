ALTER TABLE worldcup_rating_publication
    ADD COLUMN valid_vote_count BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN participant_count BIGINT NOT NULL DEFAULT 0;

-- 지도 상태 카드는 마지막으로 발행한 유효표 스냅샷을 읽는다.
CREATE TABLE worldcup_zone_vote_status
(
    zone_id                VARCHAR(50) PRIMARY KEY REFERENCES zone (id),
    valid_vote_count       INTEGER NOT NULL,
    participant_count      INTEGER NOT NULL,
    cross_zone_vote_count  INTEGER NOT NULL
);

CREATE INDEX idx_complex_map_zone ON complex (zone_id);
