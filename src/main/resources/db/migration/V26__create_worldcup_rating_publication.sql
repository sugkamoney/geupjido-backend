ALTER TABLE zone
    ADD COLUMN ranking_basis VARCHAR(10) NOT NULL DEFAULT 'INITIAL',
    ADD CONSTRAINT chk_zone_ranking_basis
        CHECK (ranking_basis IN ('INITIAL', 'VOTE'));

CREATE TABLE worldcup_rating_publication
(
    id                INTEGER PRIMARY KEY CHECK (id = 1),
    last_completed_at TIMESTAMPTZ
);

INSERT INTO worldcup_rating_publication (id, last_completed_at)
VALUES (1, NULL);
