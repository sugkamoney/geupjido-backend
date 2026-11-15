ALTER TABLE zone
    ADD COLUMN previous_published_tier NUMERIC(2, 1),
    ADD CONSTRAINT chk_zone_previous_published_tier_range
        CHECK (previous_published_tier IS NULL
            OR previous_published_tier BETWEEN 1.0 AND 5.9);

ALTER TABLE worldcup_rating_publication
    ADD COLUMN previous_completed_at TIMESTAMPTZ;
