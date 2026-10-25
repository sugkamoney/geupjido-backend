-- API 전용면적의 소수 6자리까지 반올림 없이 저장한다.
-- 기존 NUMERIC(8, 2) 값은 동일한 숫자로 보존된다.
ALTER TABLE apartment_trade
    ALTER COLUMN exclusive_area TYPE NUMERIC(12, 6);
