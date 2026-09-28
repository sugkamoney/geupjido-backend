-- 공동주택 단지에 10자리 법정동 코드를 저장한다.
ALTER TABLE complex
    ADD COLUMN legal_dong_code VARCHAR(10);

-- 값이 존재하는 경우 숫자 10자리 형식만 허용한다.
ALTER TABLE complex
    ADD CONSTRAINT chk_complex_legal_dong_code_format
        CHECK (
            legal_dong_code IS NULL
                OR legal_dong_code ~ '^[0-9]{10}$'
        );

-- 법정동 코드별 단지 조회 성능을 높인다.
CREATE INDEX idx_complex_legal_dong_code
    ON complex (legal_dong_code)
    WHERE legal_dong_code IS NOT NULL;
