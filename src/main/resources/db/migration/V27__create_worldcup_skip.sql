-- 건너뛴 판도 확정된 진행으로 저장하되 투표 집계에는 넣지 않는다.
CREATE TABLE worldcup_skip
(
    game_id       BIGINT      NOT NULL,
    round_number  SMALLINT    NOT NULL,
    skipped_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_worldcup_skip PRIMARY KEY (game_id, round_number),
    CONSTRAINT fk_worldcup_skip_game FOREIGN KEY (game_id) REFERENCES worldcup_game (id),
    CONSTRAINT chk_worldcup_skip_round CHECK (round_number BETWEEN 1 AND 10)
);

-- 선택 비율은 좌우 순서와 관계없이 같은 단지 쌍을 조회한다.
CREATE INDEX idx_worldcup_vote_pair_ratio
    ON worldcup_vote (LEAST(left_complex_id, right_complex_id),
                       GREATEST(left_complex_id, right_complex_id), selected_at, game_id, round_number);
