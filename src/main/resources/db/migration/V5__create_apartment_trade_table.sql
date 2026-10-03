-- 아파트 매매 실거래가 API에서 수집한 원본 거래를 저장한다.
CREATE TABLE apartment_trade
(
    id                 BIGSERIAL PRIMARY KEY,
    complex_id         VARCHAR(20),
    zone_id            VARCHAR(50),

    deal_date          DATE         NOT NULL,
    deal_amount        BIGINT       NOT NULL,
    exclusive_area     NUMERIC(8, 2),
    floor              SMALLINT,
    build_year         SMALLINT,

    district_code      VARCHAR(5)   NOT NULL,
    legal_dong_name    VARCHAR(50)  NOT NULL,
    raw_apartment_name VARCHAR(100) NOT NULL,
    lot_number         VARCHAR(30),
    apartment_dong     VARCHAR(50),
    dealing_type       VARCHAR(30),

    is_canceled        BOOLEAN      NOT NULL DEFAULT FALSE,
    cancellation_date  DATE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_apartment_trade_complex
        FOREIGN KEY (complex_id)
            REFERENCES complex (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_apartment_trade_zone
        FOREIGN KEY (zone_id)
            REFERENCES zone (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_apartment_trade_deal_amount_positive
        CHECK (deal_amount > 0),

    CONSTRAINT chk_apartment_trade_exclusive_area_positive
        CHECK (
            exclusive_area IS NULL
                OR exclusive_area > 0
        ),

    CONSTRAINT chk_apartment_trade_build_year_positive
        CHECK (
            build_year IS NULL
                OR build_year > 0
        ),

    CONSTRAINT chk_apartment_trade_district_code
        CHECK (district_code ~ '^[0-9]{5}$'),

    CONSTRAINT chk_apartment_trade_legal_dong_name
        CHECK (BTRIM(legal_dong_name) <> ''),

    CONSTRAINT chk_apartment_trade_apartment_name
        CHECK (BTRIM(raw_apartment_name) <> ''),

    CONSTRAINT chk_apartment_trade_cancellation
        CHECK (
            (
                is_canceled = FALSE
                    AND cancellation_date IS NULL
            )
            OR
            (
                is_canceled = TRUE
                    AND cancellation_date IS NOT NULL
                    AND cancellation_date >= deal_date
            )
        )
);

-- 같은 API 원본 거래가 반복 수집되어도 한 건만 저장되도록 한다.
CREATE UNIQUE INDEX uq_apartment_trade_source
    ON apartment_trade
    (
        district_code,
        legal_dong_name,
        raw_apartment_name,
        deal_date,
        exclusive_area,
        floor,
        deal_amount
    )
    NULLS NOT DISTINCT;

-- 단지별 최근 거래를 빠르게 조회할 수 있도록 한다.
CREATE INDEX idx_apartment_trade_complex_date
    ON apartment_trade (complex_id, deal_date DESC)
    WHERE complex_id IS NOT NULL;

-- 단지 매칭에 실패한 거래를 원본 주소와 이름으로 다시 조회한다.
CREATE INDEX idx_apartment_trade_unmatched
    ON apartment_trade
    (
        district_code,
        legal_dong_name,
        raw_apartment_name
    )
    WHERE complex_id IS NULL;
