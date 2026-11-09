ALTER TABLE zone
    ADD COLUMN initial_tier NUMERIC(2, 1);

UPDATE zone
SET initial_tier = tier;

ALTER TABLE zone
    ALTER COLUMN initial_tier SET NOT NULL;

ALTER TABLE zone
    ADD CONSTRAINT chk_zone_initial_tier_range
        CHECK (initial_tier BETWEEN 1.0 AND 5.9);
