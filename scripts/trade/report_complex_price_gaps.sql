-- 현재 대표 거래 정책(58~61㎡ → 44~65㎡ → 다른 실제 면적,
-- 각 범위에서 중개거래 우선, 그다음 직거래)으로 가격이 빈 이유를 조회한다.
-- 로컬 PostgreSQL용 읽기 전용 감사 SQL이다.
COPY (
    WITH candidate_counts AS (
        SELECT c.id, c.name, c.zone_id, c.address_jibun,
               count(t.id) AS linked_trades,
               count(t.id) FILTER (
                   WHERE t.exclusive_area > 0
               ) AS positive_area_trades,
               count(t.id) FILTER (
                   WHERE t.exclusive_area > 0
                     AND t.is_canceled = false
               ) AS noncanceled_trades,
               count(t.id) FILTER (
                   WHERE t.exclusive_area > 0
                     AND t.is_canceled = false
                     AND t.dealing_type IN ('중개거래', '직거래')
               ) AS supported_trades,
               count(t.id) FILTER (
                   WHERE t.exclusive_area > 0
                     AND t.is_canceled = false
                     AND t.dealing_type IN ('중개거래', '직거래')
                     AND NOT EXISTS (
                         SELECT 1 FROM apartment_trade_conflict x
                         WHERE x.existing_trade_id = t.id
                           AND x.review_status = 'OPEN'
                     )
               ) AS eligible_trades
        FROM complex c
        LEFT JOIN apartment_trade t
          ON t.complex_id = c.id AND t.zone_id = c.zone_id
        WHERE c.price IS NULL
        GROUP BY c.id, c.name, c.zone_id, c.address_jibun
    )
    SELECT id AS complex_id, name, zone_id, address_jibun,
           linked_trades, positive_area_trades, noncanceled_trades,
           supported_trades, eligible_trades,
           CASE
               WHEN linked_trades = 0 THEN 'NO_LINKED_TRADE'
               WHEN positive_area_trades = 0 THEN 'NO_POSITIVE_AREA_TRADE'
               WHEN noncanceled_trades = 0 THEN 'ALL_CANCELED'
               WHEN supported_trades = 0 THEN 'NO_SUPPORTED_DEALING_TYPE'
               WHEN eligible_trades = 0 THEN 'OPEN_CONFLICT'
               ELSE 'RECALCULATION_NEEDED'
           END AS reason
    FROM candidate_counts
    ORDER BY zone_id, id
) TO STDOUT WITH (FORMAT csv, HEADER true);
