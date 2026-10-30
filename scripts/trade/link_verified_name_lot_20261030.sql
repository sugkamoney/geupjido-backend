-- 로컬 초기 적재에서 이름·법정동·지번·건축연도가 모두 일치하는 두 원본 조합만 연결한다.
-- 실행 전 complex/apartment_trade 백업 필요. 재실행해도 이미 연결된 행은 바꾸지 않는다.
BEGIN;

CREATE TEMP TABLE verified_trade_group (
    district_code text NOT NULL,
    legal_dong_name text NOT NULL,
    lot_number text NOT NULL,
    raw_apartment_name text NOT NULL,
    build_year integer NOT NULL,
    complex_id text NOT NULL,
    expected_rows integer NOT NULL
) ON COMMIT DROP;

INSERT INTO verified_trade_group VALUES
    ('11410', '냉천동', '261', '돈의문센트레빌', 2011, 'A12070101', 16),
    ('41173', '호계동', '1055-1', '무궁화코오롱', 1992, 'A43179103', 22);

DO $check$
DECLARE
    group_row verified_trade_group%ROWTYPE;
    actual_rows integer;
    candidate_count integer;
    selected_count integer;
    conflict_count integer;
BEGIN
    FOR group_row IN SELECT * FROM verified_trade_group LOOP
        SELECT count(*), count(*) FILTER (WHERE c.id = group_row.complex_id)
        INTO candidate_count, selected_count
        FROM complex c
        WHERE left(c.legal_dong_code, 5) = group_row.district_code
          AND c.address_jibun ~ (
              '(^|[[:space:]])' || group_row.legal_dong_name ||
              '[[:space:]]+' || group_row.lot_number || '([[:space:]]|$)'
          )
          AND regexp_replace(c.name, '[[:space:]]+', '', 'g') =
              regexp_replace(group_row.raw_apartment_name, '[[:space:]]+', '', 'g')
          AND extract(year FROM c.approval_date) = group_row.build_year
          AND c.zone_id IS NOT NULL
          AND (c.sale_type IS NULL OR c.sale_type NOT LIKE '%임대%'
               OR c.sale_type LIKE '%분양%');
        IF candidate_count <> 1 OR selected_count <> 1 THEN
            RAISE EXCEPTION '단지 후보가 유일하지 않거나 ID가 다름: %, 후보 %, 선택 %',
                group_row.complex_id, candidate_count, selected_count;
        END IF;

        SELECT count(*) INTO actual_rows
        FROM apartment_trade t
        WHERE t.district_code = group_row.district_code
          AND t.legal_dong_name = group_row.legal_dong_name
          AND t.lot_number = group_row.lot_number
          AND t.raw_apartment_name = group_row.raw_apartment_name
          AND t.build_year = group_row.build_year
          AND (t.complex_id IS NULL OR t.complex_id = group_row.complex_id);
        IF actual_rows <> group_row.expected_rows THEN
            RAISE EXCEPTION '거래 원본 건수 변경: %, 예상 %, 실제 %',
                group_row.complex_id, group_row.expected_rows, actual_rows;
        END IF;

        SELECT count(*) INTO conflict_count
        FROM apartment_trade t
        JOIN apartment_trade_conflict x
          ON x.existing_trade_id = t.id AND x.review_status = 'OPEN'
        WHERE t.district_code = group_row.district_code
          AND t.legal_dong_name = group_row.legal_dong_name
          AND t.lot_number = group_row.lot_number
          AND t.raw_apartment_name = group_row.raw_apartment_name
          AND t.build_year = group_row.build_year;
        IF conflict_count <> 0 THEN
            RAISE EXCEPTION '미해결 거래 충돌 있음: %, 건수 %',
                group_row.complex_id, conflict_count;
        END IF;
    END LOOP;
END
$check$;

UPDATE apartment_trade t
SET complex_id = c.id, zone_id = c.zone_id
FROM verified_trade_group g
JOIN complex c ON c.id = g.complex_id
WHERE t.district_code = g.district_code
  AND t.legal_dong_name = g.legal_dong_name
  AND t.lot_number = g.lot_number
  AND t.raw_apartment_name = g.raw_apartment_name
  AND t.build_year = g.build_year
  AND t.complex_id IS NULL
  AND t.zone_id IS NULL;

DO $check$
DECLARE remaining integer;
BEGIN
    SELECT count(*) INTO remaining
    FROM verified_trade_group g
    JOIN apartment_trade t
      ON t.district_code = g.district_code
     AND t.legal_dong_name = g.legal_dong_name
     AND t.lot_number = g.lot_number
     AND t.raw_apartment_name = g.raw_apartment_name
     AND t.build_year = g.build_year
    JOIN complex c ON c.id = g.complex_id
    WHERE t.complex_id IS DISTINCT FROM c.id
       OR t.zone_id IS DISTINCT FROM c.zone_id;
    IF remaining <> 0 THEN
        RAISE EXCEPTION '연결 검증 실패: %건', remaining;
    END IF;
END
$check$;

COMMIT;
