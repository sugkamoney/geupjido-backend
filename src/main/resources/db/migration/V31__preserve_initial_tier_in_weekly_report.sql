ALTER TABLE weekly_report_zone_snapshot
    ADD COLUMN initial_tier NUMERIC(2, 1),
    ADD CONSTRAINT chk_weekly_report_zone_initial_tier
        CHECK (initial_tier IS NULL OR initial_tier BETWEEN 1.0 AND 5.9);
