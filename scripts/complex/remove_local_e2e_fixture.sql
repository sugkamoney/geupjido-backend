-- 로컬 E2E 테스트가 남긴 부산 단지 1건만 제거한다.
-- 운영 DB에는 실행하지 않는다. 예상 값이나 참조 관계가 달라지면 전체를 롤백한다.
BEGIN;

DO $cleanup$
DECLARE
    affected integer;
BEGIN
    SELECT count(*) INTO affected
    FROM complex
    WHERE id = 'A10027875'
      AND name = '괴정 경성스마트W아파트'
      AND legal_dong_code = '2638010100'
      AND address_jibun = '부산광역시 사하구 괴정동 258 괴정 경성스마트W아파트'
      AND address_road = '부산광역시 사하구 낙동대로 180'
      AND zone_id IS NULL;

    IF affected <> 1 THEN
        RAISE EXCEPTION '부산 E2E 테스트 단지의 현재 값이 예상과 다릅니다: %', affected;
    END IF;

    IF EXISTS (
        SELECT 1 FROM apartment_trade WHERE complex_id = 'A10027875'
    ) THEN
        RAISE EXCEPTION '부산 E2E 테스트 단지에 연결된 거래가 있습니다.';
    END IF;

    DELETE FROM complex WHERE id = 'A10027875';
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION '부산 E2E 테스트 단지 삭제 건수가 1이 아닙니다: %', affected;
    END IF;
END
$cleanup$;

COMMIT;
