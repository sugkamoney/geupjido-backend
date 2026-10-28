-- #91: 서울시 공동주택 통합정보마당으로 확인한 도로명주소 자치구 2건 보정.
-- 좌표, 지번주소, 법정동 코드, 권역은 변경하지 않는다.
BEGIN;

DO $review$
DECLARE
    pending integer;
    completed integer;
    affected integer;
BEGIN
    WITH expected(id, legal_dong_code, address_jibun, old_road, new_road, zone_id) AS (
        VALUES
            (
                'A13613201', '1129013600',
                '서울특별시 성북구 하월곡동 224 하월곡샹그레빌',
                '서울특별시 동대문구 회기로5길 100',
                '서울특별시 성북구 회기로5길 100',
                'seongbuk-wolgok'
            ),
            (
                'A15183001', '1159010900',
                '서울특별시 동작구 신대방동 708 해태보라매타워',
                '서울특별시 관악구 보라매로3길 29',
                '서울특별시 동작구 보라매로3길 29',
                'dongjak-sindaebang'
            )
    )
    SELECT
        count(*) FILTER (WHERE c.address_road = e.old_road),
        count(*) FILTER (WHERE c.address_road = e.new_road)
    INTO pending, completed
    FROM expected e
    JOIN complex c ON c.id = e.id
    JOIN zone z ON z.id = e.zone_id
    WHERE c.legal_dong_code = e.legal_dong_code
      AND c.address_jibun = e.address_jibun
      AND c.zone_id = e.zone_id
      AND ST_Covers(z.polygon, c.location);

    IF completed = 2 THEN
        RAISE NOTICE '도로명주소 자치구 2건이 이미 보정돼 있습니다.';
        RETURN;
    END IF;

    IF pending <> 2 OR completed <> 0 THEN
        RAISE EXCEPTION '보정 전 주소·좌표·권역 상태가 예상과 다릅니다: 대기 %, 완료 %', pending, completed;
    END IF;

    UPDATE complex
    SET address_road = CASE id
        WHEN 'A13613201' THEN '서울특별시 성북구 회기로5길 100'
        WHEN 'A15183001' THEN '서울특별시 동작구 보라매로3길 29'
    END
    WHERE id IN ('A13613201', 'A15183001');

    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 2 THEN
        RAISE EXCEPTION '도로명주소 보정 행 수가 예상과 다릅니다: %/2', affected;
    END IF;
END
$review$;

COMMIT;
