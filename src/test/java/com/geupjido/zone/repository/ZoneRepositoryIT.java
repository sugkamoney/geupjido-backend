package com.geupjido.zone.repository;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ZoneRepositoryIT {

	@Autowired
	private ZoneRepository zoneRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 권역_내부와_경계선의_좌표를_찾고_외부는_찾지_않는다() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, is_active, display_order)
			VALUES ('test-zone-lookup', '테스트', true, 999)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES (
				'TZONE001', 'test-zone-lookup', '테스트시',
				ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center)
			VALUES (
				'test-zone-lookup', 'TZONE001', '테스트권역',
				ARRAY['0000000000'],
				ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)
			)
			""");

		List<Zone> inside = zoneRepository.findCoveringZones(0.5, 0.5);
		List<Zone> boundary = zoneRepository.findCoveringZones(0.0, 0.5);
		List<Zone> outside = zoneRepository.findCoveringZones(2.0, 2.0);

		assertThat(inside).extracting(Zone::getId)
			.containsExactly("test-zone-lookup");
		assertThat(boundary).extracting(Zone::getId)
			.containsExactly("test-zone-lookup");
		assertThat(outside).isEmpty();
	}
}
