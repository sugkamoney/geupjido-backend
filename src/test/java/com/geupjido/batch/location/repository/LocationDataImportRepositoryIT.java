package com.geupjido.batch.location.repository;

import com.geupjido.batch.location.model.LegalDongBoundary;
import com.geupjido.batch.location.model.LocationMapping.CityDefinition;
import com.geupjido.batch.location.model.LocationMapping.RegionDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocationDataImportRepositoryIT {

	@Autowired
	private LegalDongBoundaryRepository boundaryRepository;

	@Autowired
	private LocationDataImportRepository importRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 법정동_경계를_병합해_city를_저장한다() {
		boundaryRepository.createTemporaryTable();

		boundaryRepository.saveAll(
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

		importRepository.upsertRegions(
			List.of(
				new RegionDefinition(
					"seoul",
					"서울",
					true,
					1
				)
			)
		);

		importRepository.upsertCities(
			List.of(
				new CityDefinition(
					"11680",
					"seoul",
					"강남구"
				)
			)
		);

		Integer matchingCityCount = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM city
				WHERE id = ?
					AND region_code = ?
					AND ST_GeometryType(polygon) = 'ST_MultiPolygon'
					AND ST_SRID(polygon) = 4326
					AND ST_Covers(polygon, center)
				""",
			Integer.class,
			"11680",
			"seoul"
		);

		assertThat(matchingCityCount).isEqualTo(1);
	}

	@Test
	void 여러_법정동_경계를_병합해_zone을_저장한다() {
		boundaryRepository.createTemporaryTable();

		boundaryRepository.saveAll(
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
				),
				new LegalDongBoundary(
					"1168010400",
					"""
						{
						  "type": "Polygon",
						  "coordinates": [
							[
							  [127.01, 37.50],
							  [127.02, 37.50],
							  [127.02, 37.51],
							  [127.01, 37.51],
							  [127.01, 37.50]
							]
						  ]
						}
						"""
				)
			)
		);

		importRepository.upsertRegions(
			List.of(
				new RegionDefinition(
					"seoul",
					"서울",
					true,
					1
				)
			)
		);

		importRepository.upsertCities(
			List.of(
				new CityDefinition(
					"11680",
					"seoul",
					"강남구"
				)
			)
		);

		importRepository.upsertZones(
			List.of(
				new ZoneDefinition(
					"gangnam-test-zone",
					"11680",
					"테스트 권역",
					List.of(
						"1168010700",
						"1168010400"
					)
				)
			)
		);

		Integer matchingZoneCount = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM zone
				WHERE id = ?
					AND city_id = ?
					AND cardinality(dong_codes) = 2
					AND dong_codes @> ARRAY[
						'1168010700',
						'1168010400'
					]::VARCHAR[]
					AND ST_GeometryType(polygon) = 'ST_MultiPolygon'
					AND ST_NumGeometries(polygon) = 1
					AND ST_Covers(polygon, center)
				""",
			Integer.class,
			"gangnam-test-zone",
			"11680"
		);

		assertThat(matchingZoneCount).isEqualTo(1);
	}
}
