-- API 응답 순서가 바뀌어도 이미 본 충돌 원본을 새 충돌로 세지 않는다.
CREATE TABLE apartment_trade_conflict_seen
(
    id                 BIGSERIAL PRIMARY KEY,
    district_code      VARCHAR(5)   NOT NULL,
    deal_year_month    VARCHAR(6)   NOT NULL,
    legal_dong_name    VARCHAR(50)  NOT NULL,
    raw_apartment_name VARCHAR(100) NOT NULL,
    lot_number         VARCHAR(30),
    apartment_dong     VARCHAR(50),
    build_year         SMALLINT,
    deal_date          DATE         NOT NULL,
    deal_amount        BIGINT       NOT NULL,
    exclusive_area     NUMERIC(12, 6),
    floor              SMALLINT,
    dealing_type       VARCHAR(30),
    is_canceled        BOOLEAN      NOT NULL,
    cancellation_date  DATE,
    registration_date  DATE,
    first_seen_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_apartment_trade_conflict_seen_source
        UNIQUE NULLS NOT DISTINCT (
            district_code, deal_year_month, legal_dong_name,
            raw_apartment_name, lot_number, apartment_dong, build_year,
            deal_date, deal_amount, exclusive_area, floor, dealing_type,
            is_canceled, cancellation_date, registration_date
        )
);

INSERT INTO apartment_trade_conflict_seen (
    district_code, deal_year_month, legal_dong_name, raw_apartment_name,
    lot_number, apartment_dong, build_year, deal_date, deal_amount,
    exclusive_area, floor, dealing_type, is_canceled, cancellation_date,
    registration_date, first_seen_at
)
SELECT
    district_code, deal_year_month, legal_dong_name, raw_apartment_name,
    lot_number, apartment_dong, build_year, deal_date, deal_amount,
    exclusive_area, floor, dealing_type, is_canceled, cancellation_date,
    registration_date, MIN(observed_at)
FROM apartment_trade_conflict
GROUP BY district_code, deal_year_month, legal_dong_name,
    raw_apartment_name, lot_number, apartment_dong, build_year,
    deal_date, deal_amount, exclusive_area, floor, dealing_type,
    is_canceled, cancellation_date, registration_date
ON CONFLICT DO NOTHING;
