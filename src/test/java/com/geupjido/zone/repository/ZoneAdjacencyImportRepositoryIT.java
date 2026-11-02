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
class ZoneAdjacencyImportRepositoryIT {

	@Autowired
	private ZoneAdjacencyImportRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 양방향_저장은_재실행해도_중복되지_않고_수동_관계를_유지한다() {
		jdbcTemplate.update("""
			INSERT INTO region (code, name, is_active, display_order)
			VALUES ('test-import', '테스트', true, 999)
			""");
		jdbcTemplate.update("""
			INSERT INTO city (id, region_code, name, polygon, center)
			VALUES (
				'TIMPORT01', 'test-import', '테스트시',
				ST_GeomFromText('MULTIPOLYGON(((0 0, 2 0, 2 1, 0 1, 0 0)))', 4326),
				ST_SetSRID(ST_MakePoint(1, 0.5), 4326)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO zone (id, city_id, name, dong_codes, polygon, center)
			VALUES
				('test-import-a', 'TIMPORT01', 'A', ARRAY['0000000001'],
				 ST_GeomFromText('MULTIPOLYGON(((0 0, 1 0, 1 1, 0 1, 0 0)))', 4326),
				 ST_SetSRID(ST_MakePoint(0.5, 0.5), 4326)),
				('test-import-b', 'TIMPORT01', 'B', ARRAY['0000000002'],
				 ST_GeomFromText('MULTIPOLYGON(((1 0, 2 0, 2 1, 1 1, 1 0)))', 4326),
				 ST_SetSRID(ST_MakePoint(1.5, 0.5), 4326))
			""");
		jdbcTemplate.update("""
			INSERT INTO zone_adjacency (zone_id, adjacent_zone_id, source)
			VALUES ('test-import-a', 'test-import-b', 'manual')
			""");

		List<ZoneIdPair> pairs = List.of(new ZoneIdPair("test-import-a", "test-import-b"));
		repository.insertAutoTouchPairs(pairs);
		repository.insertAutoTouchPairs(pairs);

		List<String> relationships = jdbcTemplate.query("""
			SELECT zone_id || '>' || adjacent_zone_id || ':' || source
			FROM zone_adjacency
			WHERE zone_id IN ('test-import-a', 'test-import-b')
			ORDER BY zone_id, adjacent_zone_id
			""", (resultSet, rowNumber) -> resultSet.getString(1));

		assertThat(relationships).containsExactly(
			"test-import-a>test-import-b:manual",
			"test-import-b>test-import-a:auto_touch"
		);
	}
}
