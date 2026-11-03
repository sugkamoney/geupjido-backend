-- 대진 스냅샷과 별개로 각 판에서 확정한 선택을 보존한다.
ALTER TABLE worldcup_game
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE worldcup_game
    ADD CONSTRAINT chk_worldcup_game_completion_time
        CHECK (completed_at IS NULL OR completed_at >= started_at);

CREATE TABLE worldcup_vote
(
    game_id             BIGINT       NOT NULL,
    round_number        SMALLINT     NOT NULL,
    left_complex_id     VARCHAR(20)  NOT NULL,
    right_complex_id    VARCHAR(20)  NOT NULL,
    selected_complex_id VARCHAR(20)  NOT NULL,
    selected_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_worldcup_vote
        PRIMARY KEY (game_id, round_number),
    CONSTRAINT fk_worldcup_vote_game
        FOREIGN KEY (game_id) REFERENCES worldcup_game (id),
    CONSTRAINT chk_worldcup_vote_round
        CHECK (round_number BETWEEN 1 AND 10),
    CONSTRAINT chk_worldcup_vote_distinct_candidates
        CHECK (left_complex_id <> right_complex_id),
    CONSTRAINT chk_worldcup_vote_selected_candidate
        CHECK (selected_complex_id IN (left_complex_id, right_complex_id))
);
