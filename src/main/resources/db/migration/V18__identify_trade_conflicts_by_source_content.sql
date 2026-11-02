-- V8의 응답 순번은 API 재조회 때 바뀔 수 있다. V17의 원본 내용 식별자로
-- 검토 행을 연결하고, 과거 중복 행은 삭제하지 않고 구분해 보존한다.
ALTER TABLE apartment_trade_conflict
    ADD COLUMN source_seen_id BIGINT;

UPDATE apartment_trade_conflict c
SET source_seen_id = s.id
FROM apartment_trade_conflict_seen s
WHERE c.district_code = s.district_code
  AND c.deal_year_month = s.deal_year_month
  AND c.legal_dong_name = s.legal_dong_name
  AND c.raw_apartment_name = s.raw_apartment_name
  AND c.lot_number IS NOT DISTINCT FROM s.lot_number
  AND c.apartment_dong IS NOT DISTINCT FROM s.apartment_dong
  AND c.build_year IS NOT DISTINCT FROM s.build_year
  AND c.deal_date = s.deal_date
  AND c.deal_amount = s.deal_amount
  AND c.exclusive_area IS NOT DISTINCT FROM s.exclusive_area
  AND c.floor IS NOT DISTINCT FROM s.floor
  AND c.dealing_type IS NOT DISTINCT FROM s.dealing_type
  AND c.is_canceled = s.is_canceled
  AND c.cancellation_date IS NOT DISTINCT FROM s.cancellation_date
  AND c.registration_date IS NOT DISTINCT FROM s.registration_date;

ALTER TABLE apartment_trade_conflict
    ALTER COLUMN source_seen_id SET NOT NULL,
    ADD CONSTRAINT fk_apartment_trade_conflict_seen
        FOREIGN KEY (source_seen_id) REFERENCES apartment_trade_conflict_seen (id),
    DROP CONSTRAINT uq_apartment_trade_conflict_source,
    DROP CONSTRAINT chk_apartment_trade_conflict_review_status;

ALTER TABLE apartment_trade_conflict
    ADD CONSTRAINT chk_apartment_trade_conflict_review_status
        CHECK (review_status IN ('OPEN', 'RESOLVED', 'SUPERSEDED'));

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY source_seen_id
               ORDER BY (existing_trade_id IS NOT NULL) DESC,
                        observed_at DESC, id DESC
           ) AS position
    FROM apartment_trade_conflict
    WHERE review_status = 'OPEN'
)
UPDATE apartment_trade_conflict c
SET review_status = 'SUPERSEDED'
FROM ranked r
WHERE c.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uq_apartment_trade_conflict_open_source_content
    ON apartment_trade_conflict (source_seen_id)
    WHERE review_status = 'OPEN';
