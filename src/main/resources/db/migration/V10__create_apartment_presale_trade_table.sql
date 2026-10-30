-- 분양권·입주권 전매 신고 원본을 일반 아파트 매매 거래와 구분해 보관한다.
CREATE TABLE apartment_presale_trade
(
    id                 BIGSERIAL PRIMARY KEY,
    complex_id         VARCHAR(20),
    zone_id            VARCHAR(50),
    district_code      VARCHAR(5)   NOT NULL,
    deal_year_month    VARCHAR(6)   NOT NULL,
    source_ordinal     INTEGER      NOT NULL,
    deal_date          DATE         NOT NULL,
    deal_amount        BIGINT       NOT NULL,
    exclusive_area     NUMERIC(12, 6) NOT NULL,
    floor              SMALLINT,
    legal_dong_name    VARCHAR(50)  NOT NULL,
    raw_apartment_name VARCHAR(100) NOT NULL,
    lot_number         VARCHAR(30),
    ownership_gbn      VARCHAR(10),
    is_canceled        BOOLEAN      NOT NULL,
    cancellation_date  DATE,
    fetched_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_presale_trade_complex
        FOREIGN KEY (complex_id) REFERENCES complex (id) ON DELETE SET NULL,
    CONSTRAINT fk_presale_trade_zone
        FOREIGN KEY (zone_id) REFERENCES zone (id) ON DELETE SET NULL,
    CONSTRAINT uq_presale_trade_source
        UNIQUE (complex_id, district_code, deal_year_month, source_ordinal),
    CONSTRAINT chk_presale_trade_district_code
        CHECK (district_code ~ '^[0-9]{5}$'),
    CONSTRAINT chk_presale_trade_month
        CHECK (deal_year_month ~ '^[0-9]{6}$'),
    CONSTRAINT chk_presale_trade_ordinal
        CHECK (source_ordinal >= 0),
    CONSTRAINT chk_presale_trade_amount
        CHECK (deal_amount > 0),
    CONSTRAINT chk_presale_trade_area
        CHECK (exclusive_area > 0),
    CONSTRAINT chk_presale_trade_cancellation
        CHECK (
            (is_canceled = FALSE AND cancellation_date IS NULL)
            OR
            (is_canceled = TRUE AND cancellation_date IS NOT NULL)
        )
);

CREATE INDEX idx_presale_trade_complex_date
    ON apartment_presale_trade (complex_id, deal_date DESC)
    WHERE complex_id IS NOT NULL;
