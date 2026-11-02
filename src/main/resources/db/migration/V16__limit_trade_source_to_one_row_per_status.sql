-- 정상·해제 행은 각각 한 건만 허용한다. 날짜만 다른 동일 상태의 중복은 막는다.
DROP INDEX uq_apartment_trade_source;

CREATE UNIQUE INDEX uq_apartment_trade_source
    ON apartment_trade (
        district_code, legal_dong_name, raw_apartment_name,
        deal_date, exclusive_area, floor, deal_amount,
        is_canceled
    ) NULLS NOT DISTINCT;
