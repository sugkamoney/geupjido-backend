-- 서비스의 최상위 지역 분류와 노출 순서를 관리한다.
CREATE TABLE region
(
    code          VARCHAR(20) PRIMARY KEY,
    name          VARCHAR(20) NOT NULL,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    display_order INTEGER     NOT NULL,

    CONSTRAINT chk_region_display_order_non_negative
        CHECK (display_order >= 0)
);

-- 시·구 단위 행정지역과 지도에 사용할 공간 정보를 저장한다.
CREATE TABLE city
(
    id          VARCHAR(10)                 PRIMARY KEY,
    region_code VARCHAR(20)                 NOT NULL,
    name        VARCHAR(50)                 NOT NULL,
    polygon     GEOMETRY(MultiPolygon, 4326) NOT NULL,
    center      GEOMETRY(Point, 4326)        NOT NULL,

    CONSTRAINT fk_city_region
        FOREIGN KEY (region_code) REFERENCES region (code)
);

-- 서비스의 급지 산정 단위인 권역과 공간·급지 정보를 저장한다.
CREATE TABLE zone
(
    id                VARCHAR(50)                  PRIMARY KEY,
    city_id           VARCHAR(10)                  NOT NULL,
    name              VARCHAR(50)                  NOT NULL,
    dong_codes        VARCHAR(20)[]                NOT NULL,
    polygon           GEOMETRY(MultiPolygon, 4326) NOT NULL,
    center            GEOMETRY(Point, 4326)        NOT NULL,
    tier              NUMERIC(2, 1),
    score             NUMERIC(8, 2),
    rank              INTEGER,
    tier_updated_at   TIMESTAMP,
    price_pyeong      INTEGER,
    price_updated_at  TIMESTAMP,

    CONSTRAINT fk_zone_city
        FOREIGN KEY (city_id) REFERENCES city (id),

    CONSTRAINT chk_zone_tier_range
        CHECK (tier IS NULL OR tier BETWEEN 1.0 AND 5.0),

    CONSTRAINT chk_zone_rank_positive
        CHECK (rank IS NULL OR rank > 0),

    CONSTRAINT chk_zone_price_non_negative
        CHECK (price_pyeong IS NULL OR price_pyeong >= 0)
);

-- 거시 대진 구성에 사용할 권역 간 인접 관계를 저장한다.
CREATE TABLE zone_adjacency
(
    zone_id          VARCHAR(50) NOT NULL,
    adjacent_zone_id VARCHAR(50) NOT NULL,
    source           VARCHAR(20) NOT NULL,

    CONSTRAINT pk_zone_adjacency
        PRIMARY KEY (zone_id, adjacent_zone_id),

    CONSTRAINT fk_zone_adjacency_zone
        FOREIGN KEY (zone_id) REFERENCES zone (id),

    CONSTRAINT fk_zone_adjacency_adjacent_zone
        FOREIGN KEY (adjacent_zone_id) REFERENCES zone (id),

    CONSTRAINT chk_zone_adjacency_different_zone
        CHECK (zone_id <> adjacent_zone_id),

    CONSTRAINT chk_zone_adjacency_source
        CHECK (source IN ('auto_city', 'auto_touch', 'manual'))
);

-- 외부 API에서 수집한 아파트 단지와 위치·시세 정보를 저장한다.
CREATE TABLE complex
(
    id                  VARCHAR(20)          PRIMARY KEY,
    zone_id             VARCHAR(50),
    name                VARCHAR(100)         NOT NULL,
    address_jibun       VARCHAR(200)         NOT NULL,
    address_road        VARCHAR(200),
    location            GEOMETRY(Point, 4326),
    households          INTEGER              NOT NULL,
    building_count      INTEGER,
    approval_date       DATE,
    score               NUMERIC(8, 2),
    zone_rank           INTEGER,
    price               INTEGER,
    representative_area NUMERIC(6, 2),
    last_deal_date      DATE,
    is_active           BOOLEAN              NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_complex_zone
        FOREIGN KEY (zone_id) REFERENCES zone (id),

    CONSTRAINT chk_complex_households_non_negative
        CHECK (households >= 0),

    CONSTRAINT chk_complex_building_count_positive
        CHECK (building_count IS NULL OR building_count > 0),

    CONSTRAINT chk_complex_zone_rank_positive
        CHECK (zone_rank IS NULL OR zone_rank > 0),

    CONSTRAINT chk_complex_price_non_negative
        CHECK (price IS NULL OR price >= 0),

    CONSTRAINT chk_complex_representative_area_positive
        CHECK (representative_area IS NULL OR representative_area > 0)
);

-- 공간 포함 여부와 위치 검색 성능을 높인다.
CREATE INDEX idx_city_polygon
    ON city USING GIST (polygon);

CREATE INDEX idx_zone_polygon
    ON zone USING GIST (polygon);

CREATE INDEX idx_complex_location
    ON complex USING GIST (location);

-- 활성 단지를 권역별 점수순으로 조회할 때 사용한다.
CREATE INDEX idx_complex_zone
    ON complex (zone_id, score DESC)
    WHERE is_active = TRUE;
