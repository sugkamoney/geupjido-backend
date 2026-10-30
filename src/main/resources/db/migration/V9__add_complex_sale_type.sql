-- 공동주택 기본정보 API의 분양형태 원문을 보존한다.
-- 분양/임대가 함께 있는 값이나 미확인 값은 매칭에서 별도로 판단한다.
ALTER TABLE complex
    ADD COLUMN sale_type VARCHAR(50);
