CREATE INDEX idx_worldcup_game_anonymous_started
    ON worldcup_game (anonymous_id, started_at);
