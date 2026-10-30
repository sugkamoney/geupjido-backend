-- 대표 거래의 실제 전용면적을 원본과 같은 정밀도로 보관한다.
ALTER TABLE complex
    ALTER COLUMN representative_area TYPE NUMERIC(12, 6);
