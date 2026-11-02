ALTER TABLE apartment_trade_conflict
    DROP CONSTRAINT chk_apartment_trade_conflict_reason;

ALTER TABLE apartment_trade_conflict
    ADD CONSTRAINT chk_apartment_trade_conflict_reason
        CHECK (conflict_reason IN (
            'SOURCE_STATUS_CONFLICT', 'SOURCE_CANCELLATION_DATE_MISMATCH',
            'CANCELED_TO_NORMAL', 'CANCELLATION_DATE_MISMATCH',
            'SOURCE_REGISTRATION_DATE_MISMATCH',
            'REGISTRATION_DATE_MISMATCH', 'CANCELED_BEFORE_REGISTRATION'
        ));
