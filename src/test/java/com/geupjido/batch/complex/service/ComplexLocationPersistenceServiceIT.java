package com.geupjido.batch.complex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ComplexLocationPersistenceServiceIT {

	private static final String COMPLEX_ID = "T00000090";
	private static final String ZONE_ID = "test-location-save";

	private final GeometryFactory geometryFactory =
		new GeometryFactory(new PrecisionModel(), 4326);

	@Autowired
	private ComplexLocationPersistenceService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, is_active, display_order)
			VALUES ('test-location-save', '테스트', true, 999)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES (
				'TLOC0001', 'test-location-save', '테스트시',
				ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center)
			VALUES (
				'test-location-save', 'TLOC0001', '테스트권역',
				ARRAY['0000000000'],
				ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO complex (
				id, name, address_jibun, legal_dong_code, households, is_active
			)
			VALUES (
				'T00000090', '테스트 단지', '테스트 주소',
				'0000000000', 100, false
			)
			""");
	}

	@Test
	void 매칭된_좌표와_권역을_함께_저장하고_재실행에서_건너뛴다() {
		var first = service.saveLocationIfAbsent(
			COMPLEX_ID, point(0.5, 0.5)
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(first).isPresent();
		assertThat(first.orElseThrow().status())
			.isEqualTo(ComplexZoneMatchService.Status.MATCHED);
		assertThat(savedZoneId()).isEqualTo(ZONE_ID);
		assertThat(savedLongitude()).isEqualTo(0.5);
		assertThat(savedSrid()).isEqualTo(4326);

		var second = service.saveLocationIfAbsent(
			COMPLEX_ID, point(0.75, 0.75)
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(second).isEmpty();
		assertThat(savedLongitude()).isEqualTo(0.5);
		assertThat(savedZoneId()).isEqualTo(ZONE_ID);
	}

	@Test
	void 권역_밖의_좌표는_좌표만_저장한다() {
		var result = service.saveLocationIfAbsent(
			COMPLEX_ID, point(2.0, 2.0)
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(result).isPresent();
		assertThat(result.orElseThrow().status())
			.isEqualTo(ComplexZoneMatchService.Status.OUTSIDE);
		assertThat(savedLongitude()).isEqualTo(2.0);
		assertThat(savedSrid()).isEqualTo(4326);
		assertThat(savedZoneId()).isNull();
	}

	@Test
	void 저장된_좌표로_권역을_재판정하고_재실행에서_건너뛴다() {
		assertThat(service.assignZoneIfAbsent(COMPLEX_ID)).isEmpty();
		jdbcTemplate.update("""
			UPDATE complex
			SET location = ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			WHERE id = ?
			""", COMPLEX_ID);
		entityManager.clear();

		var first = service.assignZoneIfAbsent(COMPLEX_ID);
		entityManager.flush();
		entityManager.clear();

		assertThat(first).isPresent();
		assertThat(first.orElseThrow().status())
			.isEqualTo(ComplexZoneMatchService.Status.MATCHED);
		assertThat(savedZoneId()).isEqualTo(ZONE_ID);
		assertThat(savedLongitude()).isEqualTo(0.5);

		assertThat(service.assignZoneIfAbsent(COMPLEX_ID)).isEmpty();
	}

	private String savedZoneId() {
		return jdbcTemplate.queryForObject(
			"SELECT zone_id FROM complex WHERE id = ?",
			String.class, COMPLEX_ID
		);
	}

	private Double savedLongitude() {
		return jdbcTemplate.queryForObject(
			"SELECT ST_X(location) FROM complex WHERE id = ?",
			Double.class, COMPLEX_ID
		);
	}

	private Integer savedSrid() {
		return jdbcTemplate.queryForObject(
			"SELECT ST_SRID(location) FROM complex WHERE id = ?",
			Integer.class, COMPLEX_ID
		);
	}

	private Point point(double longitude, double latitude) {
		return geometryFactory.createPoint(
			new Coordinate(longitude, latitude)
		);
	}
}
