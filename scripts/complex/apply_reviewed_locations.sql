-- 2026-10-27 사용자 확인에 따른 로컬 단지 주소·권역 보정.
-- Flyway 스키마 변경이 아닌 수동 데이터 보정이며, 각 조건이 맞지 않으면 전체 롤백한다.
-- 마장동은 현재 162권역에 없으므로 A10020204의 zone_id는 비워 둔다.

BEGIN;

DO $review$
DECLARE
    affected integer;
BEGIN
    PERFORM id
    FROM complex
    WHERE id IN (
        'A10020204', 'A10027572', 'A10025641', 'A15722001',
        'A15722004', 'A15722011', 'A15722104', 'A15722106',
        'A15784601'
    )
    FOR UPDATE;

    SELECT count(*) INTO affected
    FROM complex c
    WHERE c.id = 'A10020204'
      AND c.name = '마장한영스테이'
      AND c.address_road = '서울특별시 성동구 마장로42길 6'
      AND c.address_jibun IN (
          '서울특별시 노원구 공릉동 596-27 마장한영스테이',
          '서울특별시 성동구 마장동 783-5 한영스테이'
      )
      AND c.legal_dong_code IN ('1135010300', '1120010500')
      AND c.zone_id IS NULL
      AND ST_DWithin(
          c.location::geography,
          ST_SetSRID(ST_MakePoint(127.0444954, 37.5662983), 4326)::geography,
          1
      );
    IF affected <> 1 THEN
        RAISE EXCEPTION 'A10020204 원본 주소·좌표가 예상과 다릅니다.';
    END IF;

    SELECT count(*) INTO affected
    FROM complex c
    JOIN zone z ON z.id = 'mapo-mangwon-hapjeong'
    WHERE c.id = 'A10027572'
      AND c.name = '마포영화블렌하임'
      AND c.address_jibun = '서울특별시 마포구 망원동 517 마포영화블렌하임'
      AND c.address_road IN (
          '경기도 고양시 덕양구 호국로 1000',
          '서울특별시 마포구 월드컵로25길 26 마포영화블렌하임'
      )
      AND c.legal_dong_code = '1144012300'
      AND (c.zone_id IS NULL OR c.zone_id = z.id)
      AND (
          ST_DWithin(
              c.location::geography,
              ST_SetSRID(ST_MakePoint(126.8512227, 37.6713015), 4326)::geography,
              1
          )
          OR ST_DWithin(
              c.location::geography,
              ST_SetSRID(ST_MakePoint(126.9063069, 37.5584937), 4326)::geography,
              1
          )
      )
      AND ST_Contains(
          z.polygon,
          ST_SetSRID(ST_MakePoint(126.9063069, 37.5584937), 4326)
      )
      AND c.legal_dong_code = ANY(z.dong_codes);
    IF affected <> 1 THEN
        RAISE EXCEPTION 'A10027572 원본 주소·좌표 또는 망원 권역이 예상과 다릅니다.';
    END IF;

    WITH expected(id, address_jibun) AS (
        VALUES
            ('A10025641', '서울특별시 강서구 방화동 883 마곡보타닉경남아너스빌아파트'),
            ('A15722001', '서울특별시 강서구 방화동 857 마곡삼성'),
            ('A15722004', '서울특별시 강서구 방화동 890 마곡푸르지오'),
            ('A15722011', '서울특별시 강서구 방화동 201-1 방화길훈'),
            ('A15722104', '서울특별시 강서구 방화동 859 마곡청구아파트'),
            ('A15722106', '서울특별시 강서구 방화동 876 마곡우림필유아파트'),
            ('A15784601', '서울특별시 강서구 방화동 863 마곡현대아파트')
    )
    SELECT count(*) INTO affected
    FROM expected e
    JOIN complex c ON c.id = e.id
    JOIN zone z ON z.id = 'gangseo-magok'
    WHERE c.address_jibun = e.address_jibun
      AND c.legal_dong_code = '1150010900'
      AND (c.zone_id IS NULL OR c.zone_id = z.id)
      AND ST_Contains(z.polygon, c.location)
      AND (
          SELECT count(*) FROM zone covering
          WHERE ST_Covers(covering.polygon, c.location)
      ) = 1;
    IF affected <> 7 THEN
        RAISE EXCEPTION '마곡 수동 예외 7건의 주소·좌표·권역 조건이 예상과 다릅니다: %', affected;
    END IF;

    UPDATE complex
    SET address_jibun = '서울특별시 성동구 마장동 783-5 한영스테이',
        legal_dong_code = '1120010500'
    WHERE id = 'A10020204';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION 'A10020204 보정 행 수가 1이 아닙니다.';
    END IF;

    UPDATE complex
    SET address_road = '서울특별시 마포구 월드컵로25길 26 마포영화블렌하임',
        location = ST_SetSRID(ST_MakePoint(126.9063069, 37.5584937), 4326),
        zone_id = 'mapo-mangwon-hapjeong'
    WHERE id = 'A10027572';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION 'A10027572 보정 행 수가 1이 아닙니다.';
    END IF;

    UPDATE complex
    SET zone_id = 'gangseo-magok'
    WHERE id IN (
        'A10025641', 'A15722001', 'A15722004', 'A15722011',
        'A15722104', 'A15722106', 'A15784601'
    );
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 7 THEN
        RAISE EXCEPTION '마곡 수동 예외 보정 행 수가 7이 아닙니다.';
    END IF;
END
$review$;

COMMIT;
