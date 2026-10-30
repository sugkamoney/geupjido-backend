CREATE INDEX IF NOT EXISTS idx_apartment_trade_conflict_existing_trade_id
    ON apartment_trade_conflict (existing_trade_id)
    WHERE existing_trade_id IS NOT NULL;
