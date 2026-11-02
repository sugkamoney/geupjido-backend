-- 등기 완료일은 동일 계약 조건의 정상·해제 원본을 검토할 때 필요한 증거다.
ALTER TABLE apartment_trade
    ADD COLUMN registration_date DATE;

ALTER TABLE apartment_trade_conflict
    ADD COLUMN registration_date DATE;
