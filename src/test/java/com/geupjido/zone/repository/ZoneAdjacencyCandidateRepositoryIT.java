package com.geupjido.zone.repository;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.zone.repository.ZoneAdjacencyCandidateRepository.ZoneIdPair;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ZoneAdjacencyCandidateRepositoryIT {

	@Autowired
	private ZoneAdjacencyCandidateRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 맞닿은_권역만_한_방향의_후보로_조회한다() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, is_active, display_order)
			VALUES ('test-touch', '테스트', true, 999)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES (
				'TTOUCH001', 'test-touch', '테스트시',
				ST_GeomFromText('MULTIPOLYGON(((0 0, 4 0, 4 4, 0 4, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(2, 2), 4326)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center)
			VALUES
				('test-touch-a', 'TTOUCH001', 'A', ARRAY['0000000001'],
				 ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				 ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)),
				('test-touch-b', 'TTOUCH001', 'B', ARRAY['0000000002'],
				 ST_GeomFromText('MULTIPOLYGON(((1 0, 2 0, 2 1, 1 1, 1 0)))', 4326),
				 ST_SetSRID(ST_MakePoint(1.5, 0.5), 4326)),
				('test-touch-overlap', 'TTOUCH001', '겹침', ARRAY['0000000003'],
				 ST_GeomFromText('MULTIPOLYGON(((0.5 0.2, 1.5 0.2, 1.5 0.8, 0.5 0.8, 0.5 0.2)))', 4326),
				 ST_SetSRID(ST_MakePoint(1, 0.5), 4326)),
				('test-touch-far', 'TTOUCH001', '떨어짐', ARRAY['0000000004'],
				 ST_GeomFromText('MULTIPOLYGON(((3 3, 4 3, 4 4, 3 4, 3 3)))', 4326),
				 ST_SetSRID(ST_MakePoint(3.5, 3.5), 4326))
			""");

		List<ZoneIdPair> testPairs = repository.findTouchingPairs().stream()
			.filter(pair -> pair.zoneId().startsWith("test-touch-"))
			.toList();

		assertThat(testPairs).containsExactly(
			new ZoneIdPair("test-touch-a", "test-touch-b")
		);
	}
}
