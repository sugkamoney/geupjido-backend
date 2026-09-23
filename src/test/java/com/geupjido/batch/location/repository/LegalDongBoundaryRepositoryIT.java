package com.geupjido.batch.location.repository;

import com.geupjido.batch.location.model.LegalDongBoundary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LegalDongBoundaryRepositoryIT {

	@Autowired
	private LegalDongBoundaryRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void GeoJSON_경계를_PostGIS_MultiPolygon으로_저장한다() {
		repository.createTemporaryTable();

		repository.saveAll(
			List.of(
				new LegalDongBoundary(
					"1168010700",
					"""
						{
						  "type": "Polygon",
						  "coordinates": [
						    [
						      [127.00, 37.50],
						      [127.01, 37.50],
						      [127.01, 37.51],
						      [127.00, 37.51],
						      [127.00, 37.50]
						    ]
						  ]
						}
						"""
				)
			)
		);

		Integer count = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM legal_dong_boundary_stage
				""",
			Integer.class
		);

		String geometryType = jdbcTemplate.queryForObject(
			"""
				SELECT ST_GeometryType(geometry)
				FROM legal_dong_boundary_stage
				WHERE dong_code = ?
				""",
			String.class,
			"1168010700"
		);

		Integer srid = jdbcTemplate.queryForObject(
			"""
				SELECT ST_SRID(geometry)
				FROM legal_dong_boundary_stage
				WHERE dong_code = ?
				""",
			Integer.class,
			"1168010700"
		);

		assertThat(count).isEqualTo(1);
		assertThat(geometryType).isEqualTo("ST_MultiPolygon");
		assertThat(srid).isEqualTo(4326);
	}
}
