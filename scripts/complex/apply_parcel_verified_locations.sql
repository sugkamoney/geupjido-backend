-- #91: VWorld 연속지적도 PNU 필지 안의 지번 후보 점 11건을 로컬 DB에 반영한다.
-- 사당동 42(A10024245)는 해당 PNU 필지가 조회되지 않아 제외한다.
-- 사용 전 docs/history/2026-10-27-complex-location-exception-followup.md를 확인한다.
BEGIN;

CREATE TEMP TABLE reviewed_location (
    id varchar(20) PRIMARY KEY,
    legal_dong_code varchar(10) NOT NULL,
    address_jibun varchar(200) NOT NULL,
    address_road varchar(200) NOT NULL,
    zone_id varchar(100) NOT NULL,
    old_x double precision,
    old_y double precision,
    new_x double precision NOT NULL,
    new_y double precision NOT NULL
) ON COMMIT DROP;

INSERT INTO reviewed_location VALUES
    ('A10019943', '1130510100', '서울특별시 강북구 미아동 195 엘리프미아역', '서울특별시 강북구 도봉로 232', 'gangbuk-mia', NULL, NULL, 127.0257000, 37.6287092),
    ('A10025753', '1129013400', '서울특별시 성북구 길음동 1287 길음뉴타운11단지 롯데캐슬골든힐스아파트', '서울특별시 성북구 서경로 60', 'seongbuk-gileum', 127.0161403, 37.6089051, 127.0163450, 37.6088281),
    ('A10026719', '1117012500', '서울특별시 용산구 한강로2가 419- 아스테리움용산', '서울특별시 용산구 한강대로30길 25', 'yongsan-hangangro', 126.9688617, 37.5273586, 126.9688574, 37.5276243),
    ('A10027424', '1159010200', '서울특별시 동작구 상도동 363 상도파크자이 아파트', '서울특별시 동작구 장승배기로11가길 11', 'dongjak-sangdo', 126.9384711, 37.5058646, 126.9368118, 37.5054736),
    ('A12125202', '1144012500', '서울특별시 마포구 성산동 601 성산월드타운대림', '서울특별시 마포구 월드컵북로30길 9-22', 'mapo-seongsan', 126.9076956, 37.5698541, 126.9082545, 37.5693561),
    ('A12201301', '1138010700', '서울특별시 은평구 응암동 714 경남아파트-응암경남', '서울특별시 은평구 가좌로7나길 35', 'eunpyeong-eungam', 126.9200267, 37.5831319, 126.9203758, 37.5832834),
    ('A13185607', '1126010200', '서울특별시 중랑구 상봉동 63 신내건영2차아파트', '서울특별시 중랑구 신내로7길 20', 'jungrang-sangbong-junghwa', 127.0960991, 37.6013912, 127.0929430, 37.6012033),
    ('A13276415', '1132010600', '서울특별시 도봉구 방학동 734 방학청구', '서울특별시 도봉구 해등로 264', 'dobong-banghak', 127.0276067, 37.6583132, 127.0261532, 37.6580227),
    ('A13613201', '1129013600', '서울특별시 성북구 하월곡동 224 하월곡샹그레빌', '서울특별시 동대문구 회기로5길 100', 'seongbuk-wolgok', 127.041007, 37.5952384, 127.0414091, 37.5964495),
    ('A15183001', '1159010900', '서울특별시 동작구 신대방동 708 해태보라매타워', '서울특별시 관악구 보라매로3길 29', 'dongjak-sindaebang', 126.9249903, 37.4912113, 126.9248008, 37.4912446),
    ('A15603003', '1159010200', '서울특별시 동작구 상도동 414 상도건영제2 관리사무소', '서울특별시 동작구 만양로 26', 'dongjak-sangdo', 126.9499848, 37.5077626, 126.9497864, 37.5071667);

DO $review$
DECLARE
    completed integer;
    pending integer;
    valid_candidates integer;
    affected integer;
BEGIN
    SELECT count(*) INTO completed
    FROM reviewed_location e
    JOIN complex c ON c.id = e.id
    WHERE c.legal_dong_code = e.legal_dong_code
      AND c.address_jibun = e.address_jibun
      AND (
          c.address_road = e.address_road
          OR (e.id = 'A13613201' AND c.address_road = '서울특별시 성북구 회기로5길 100')
          OR (e.id = 'A15183001' AND c.address_road = '서울특별시 동작구 보라매로3길 29')
      )
      AND c.zone_id = e.zone_id
      AND ST_DWithin(
          c.location::geography,
          ST_SetSRID(ST_MakePoint(e.new_x, e.new_y), 4326)::geography,
          1
      );

    IF completed = 11 THEN
        RAISE NOTICE '검증된 단지 11건이 이미 배정돼 있습니다.';
        RETURN;
    END IF;

    SELECT count(*) INTO pending
    FROM reviewed_location e
    JOIN complex c ON c.id = e.id
    WHERE c.legal_dong_code = e.legal_dong_code
      AND c.address_jibun = e.address_jibun
      AND c.address_road = e.address_road
      AND c.zone_id IS NULL
      AND (
          (e.old_x IS NULL AND e.old_y IS NULL AND c.location IS NULL)
          OR (
              e.old_x IS NOT NULL AND e.old_y IS NOT NULL
              AND ST_DWithin(
                  c.location::geography,
                  ST_SetSRID(ST_MakePoint(e.old_x, e.old_y), 4326)::geography,
                  1
              )
          )
      );

    IF pending <> 11 THEN
        RAISE EXCEPTION '원본 주소·좌표·권역 상태가 예상과 다릅니다: %/11', pending;
    END IF;

    SELECT count(*) INTO valid_candidates
    FROM reviewed_location e
    JOIN zone z ON z.id = e.zone_id
    WHERE e.legal_dong_code = ANY(z.dong_codes)
      AND ST_Covers(z.polygon, ST_SetSRID(ST_MakePoint(e.new_x, e.new_y), 4326))
      AND (
          SELECT count(*) FROM zone covering
          WHERE ST_Covers(
              covering.polygon,
              ST_SetSRID(ST_MakePoint(e.new_x, e.new_y), 4326)
          )
      ) = 1;

    IF valid_candidates <> 11 THEN
        RAISE EXCEPTION '후보 좌표와 권역의 공간·법정동 검증이 실패했습니다: %/11', valid_candidates;
    END IF;

    UPDATE complex c
    SET location = ST_SetSRID(ST_MakePoint(e.new_x, e.new_y), 4326),
        zone_id = e.zone_id
    FROM reviewed_location e
    WHERE c.id = e.id;

    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected <> 11 THEN
        RAISE EXCEPTION '좌표·권역 보정 행 수가 예상과 다릅니다: %/11', affected;
    END IF;
END
$review$;

COMMIT;
