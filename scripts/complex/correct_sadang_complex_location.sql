-- #91: A10024245의 현행 주소·필지 확인 결과를 로컬 DB에 반영한다.
-- 서울시 공동주택 통합정보마당: 동작대로39길 39.
-- 네이버 주소 조회: 위 도로명주소 = 사당동 1161.
-- VWorld 현재 연속지적도: PNU 1159010700111610000 내 후보 점 확인.
-- 원본 K-apt 지번 사당동 42는 별도 근거 없이 다른 단지에 적용하지 않는다.
BEGIN;

DO $review$
DECLARE
    target complex%ROWTYPE;
    candidate geometry(Point, 4326) := ST_SetSRID(ST_MakePoint(126.9802205, 37.4924140), 4326);
    covering_count integer;
    affected integer;
BEGIN
    SELECT * INTO target FROM complex WHERE id = 'A10024245' FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION '대상 단지가 없습니다: A10024245';
    END IF;

    IF target.name <> '이수푸르지오더프레티움'
       OR target.legal_dong_code <> '1159010700'
       OR target.address_road <> '서울특별시 동작구 동작대로39길 39' THEN
        RAISE EXCEPTION '대상 단지의 이름·법정동 코드·도로명주소가 예상과 다릅니다.';
    END IF;

    SELECT count(*) INTO covering_count
    FROM zone z
    WHERE ST_Covers(z.polygon, candidate);

    IF covering_count <> 1
       OR NOT EXISTS (
           SELECT 1 FROM zone z
           WHERE z.id = 'dongjak-sadang'
             AND target.legal_dong_code = ANY(z.dong_codes)
             AND ST_Covers(z.polygon, candidate)
       ) THEN
        RAISE EXCEPTION '사당동 1161 필지 후보 점의 권역·법정동 검증이 실패했습니다.';
    END IF;

    IF target.address_jibun = '서울특별시 동작구 사당동 1161 이수푸르지오더프레티움'
       AND target.zone_id = 'dongjak-sadang'
       AND ST_DWithin(target.location::geography, candidate::geography, 1) THEN
        RAISE NOTICE '사당동 단지의 지번·좌표·권역이 이미 보정돼 있습니다.';
        RETURN;
    END IF;

    IF target.address_jibun <> '서울특별시 동작구 사당동 42 이수푸르지오더프레티움'
       OR target.zone_id IS NOT NULL
       OR target.location IS NULL
       OR NOT ST_DWithin(
           target.location::geography,
           ST_SetSRID(ST_MakePoint(126.9804639, 37.4930732), 4326)::geography,
           1
       ) THEN
        RAISE EXCEPTION '보정 전 지번·좌표·권역 상태가 예상과 다릅니다.';
    END IF;

    UPDATE complex
    SET address_jibun = '서울특별시 동작구 사당동 1161 이수푸르지오더프레티움',
        location = candidate,
        zone_id = 'dongjak-sadang'
    WHERE id = 'A10024245';

    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 1 THEN
        RAISE EXCEPTION '사당동 단지 보정 행 수가 예상과 다릅니다: %/1', affected;
    END IF;
END
$review$;

COMMIT;
