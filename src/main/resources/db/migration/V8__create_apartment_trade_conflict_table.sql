-- 같은 거래 식별 키의 상태 또는 해제일이 충돌한 API 응답을 검토용으로 보존한다.
CREATE TABLE apartment_trade_conflict
(
    id                  BIGSERIAL PRIMARY KEY,
    district_code       VARCHAR(5)   NOT NULL,
    deal_year_month     VARCHAR(6)   NOT NULL,
    source_ordinal      INTEGER      NOT NULL,
    conflict_reason     VARCHAR(40)  NOT NULL,
    review_status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    existing_trade_id   BIGINT REFERENCES apartment_trade (id),
    legal_dong_name     VARCHAR(50)  NOT NULL,
    raw_apartment_name  VARCHAR(100) NOT NULL,
    lot_number          VARCHAR(30),
    apartment_dong      VARCHAR(50),
    build_year          SMALLINT,
    deal_date           DATE         NOT NULL,
    deal_amount         BIGINT       NOT NULL,
    exclusive_area      NUMERIC(12, 6),
    floor               SMALLINT,
    dealing_type        VARCHAR(30),
    is_canceled         BOOLEAN      NOT NULL,
    cancellation_date   DATE,
    observed_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_apartment_trade_conflict_source
        UNIQUE (district_code, deal_year_month, source_ordinal),
    CONSTRAINT chk_apartment_trade_conflict_reason
        CHECK (conflict_reason IN (
            'SOURCE_STATUS_CONFLICT', 'SOURCE_CANCELLATION_DATE_MISMATCH',
            'CANCELED_TO_NORMAL', 'CANCELLATION_DATE_MISMATCH'
        )),
    CONSTRAINT chk_apartment_trade_conflict_review_status
        CHECK (review_status IN ('OPEN', 'RESOLVED'))
);

CREATE INDEX idx_apartment_trade_conflict_open
    ON apartment_trade_conflict (district_code, deal_year_month, raw_apartment_name)
    WHERE review_status = 'OPEN';

CREATE INDEX idx_apartment_trade_conflict_existing_open
    ON apartment_trade_conflict (existing_trade_id)
    WHERE review_status = 'OPEN' AND existing_trade_id IS NOT NULL;
