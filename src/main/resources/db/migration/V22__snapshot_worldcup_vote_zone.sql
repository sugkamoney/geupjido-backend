ALTER TABLE worldcup_vote
    ADD COLUMN left_zone_id VARCHAR(50),
    ADD COLUMN right_zone_id VARCHAR(50);

UPDATE worldcup_vote AS vote
SET left_zone_id = left_complex.zone_id,
    right_zone_id = right_complex.zone_id
FROM complex AS left_complex, complex AS right_complex
WHERE vote.left_complex_id = left_complex.id
  AND vote.right_complex_id = right_complex.id;

ALTER TABLE worldcup_vote
    ALTER COLUMN left_zone_id SET NOT NULL,
    ALTER COLUMN right_zone_id SET NOT NULL,
    ADD CONSTRAINT fk_worldcup_vote_left_zone
        FOREIGN KEY (left_zone_id) REFERENCES zone (id),
    ADD CONSTRAINT fk_worldcup_vote_right_zone
        FOREIGN KEY (right_zone_id) REFERENCES zone (id);

CREATE INDEX idx_worldcup_vote_aggregation_order
    ON worldcup_vote (selected_at, game_id, round_number);
