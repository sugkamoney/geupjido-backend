-- 등기일이 확인된 별도 거래를 기존 동일 계약 조건의 해제 거래와 함께 보존한다.
-- 등기일이 없는 원본의 중복 방지는 기존과 같이 NULLS NOT DISTINCT로 유지한다.
DROP INDEX uq_apartment_trade_source;

CREATE UNIQUE INDEX uq_apartment_trade_source
    ON apartment_trade (
        district_code, legal_dong_name, raw_apartment_name,
        deal_date, exclusive_area, floor, deal_amount,
        registration_date
    ) NULLS NOT DISTINCT;
