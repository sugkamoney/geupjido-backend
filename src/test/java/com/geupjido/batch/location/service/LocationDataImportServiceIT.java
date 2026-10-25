package com.geupjido.batch.location.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.geupjido.batch.location.exception.LocationMappingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class LocationDataImportServiceIT {

	@TempDir
	Path tempDirectory;

	@Autowired
	private LocationDataImportService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 포일동과_내손동_경계를_한_권역으로_합쳐_저장한다()
		throws IOException {
		Path mappingPath = tempDirectory.resolve(
			"location-mapping.json"
		);
		Path boundaryPath = tempDirectory.resolve(
			"legal-dong-boundary.geojson"
		);

		Files.writeString(mappingPath, """
			{
			  "regions": [
			    {
		      "code": "gyeonggi",
		      "name": "경기",
			      "active": true,
			      "displayOrder": 1
			    }
			  ],
			  "cities": [
			    {
		      "id": "41430",
		      "regionCode": "gyeonggi",
		      "name": "의왕시"
			    }
			  ],
			  "zones": [
			    {
		      "id": "integration-test-poil-naeson",
		      "cityId": "41430",
		      "name": "포일/내손",
			      "dongCodes": [
		        "4143010700",
		        "4143010900"
			      ],
		       "initialTier": 3.6
			    }
			  ]
			}
			""");

		Files.writeString(boundaryPath, """
			{
			  "type": "FeatureCollection",
			  "features": [
			    {
			      "type": "Feature",
			      "properties": {
		        "EMD_CD": "4143010700",
		        "EMD_NM": "내손동"
			      },
			      "geometry": {
			        "type": "Polygon",
			        "coordinates": [
			          [
		            [127.10, 37.30],
		            [127.11, 37.30],
		            [127.11, 37.31],
		            [127.10, 37.31],
		            [127.10, 37.30]
			          ]
			        ]
			      }
		    },
		    {
		      "type": "Feature",
		      "properties": {
		        "EMD_CD": "4143010900",
		        "EMD_NM": "포일동"
		      },
		      "geometry": {
		        "type": "Polygon",
		        "coordinates": [
		          [
		            [127.11, 37.30],
		            [127.12, 37.30],
		            [127.12, 37.31],
		            [127.11, 37.31],
		            [127.11, 37.30]
		          ]
		        ]
		      }
			    }
			  ]
			}
			""");

		service.importLocationData(
			mappingPath,
			boundaryPath,
			"EMD_CD"
		);

		Integer importedZoneCount = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM zone z
				JOIN city c
					ON c.id = z.city_id
				JOIN region r
					ON r.code = c.region_code
				WHERE z.id = ?
					AND z.tier = 3.6
					AND z.dong_codes = ARRAY[
						'4143010700',
						'4143010900'
					]::VARCHAR[]
					AND ST_GeometryType(z.polygon)
						= 'ST_MultiPolygon'
					AND ST_SRID(z.polygon) = 4326
					AND ST_Covers(z.polygon, z.center)
					AND ST_Covers(z.polygon, ST_SetSRID(ST_Point(127.105, 37.305), 4326))
					AND ST_Covers(z.polygon, ST_SetSRID(ST_Point(127.115, 37.305), 4326))
					AND c.name = '의왕시'
					AND r.name = '경기'
				""",
			Integer.class,
			"integration-test-poil-naeson"
		);

		assertThat(importedZoneCount).isEqualTo(1);
	}

	@Test
	void 실제_경계에서_포일동과_내손동이_같은_권역에_포함된다() {
		Path mappingPath = Path.of("data/location/mapping/location-mapping.json");
		Path boundaryPath = Path.of("data/location/boundary/legal-dong-boundaries.geojson");

		Assumptions.assumeTrue(
			Files.exists(boundaryPath),
			"로컬 법정동 경계 GeoJSON이 필요합니다."
		);

		service.importLocationData(mappingPath, boundaryPath, "emd_cd");

		Integer importedZoneCount = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM zone z
				WHERE z.id = 'uiwang-naeson'
					AND z.name = '포일/내손'
					AND z.dong_codes = ARRAY[
						'4143010700', '4143010900'
					]::VARCHAR[]
					AND ST_Covers(
						z.polygon,
						(
							SELECT ST_PointOnSurface(geometry)
							FROM legal_dong_boundary_stage
							WHERE dong_code = '4143010700'
						)
					)
					AND ST_Covers(
						z.polygon,
						(
							SELECT ST_PointOnSurface(geometry)
							FROM legal_dong_boundary_stage
							WHERE dong_code = '4143010900'
						)
					)
				""",
			Integer.class
		);

		assertThat(importedZoneCount).isEqualTo(1);
	}

	@Test
	void GeoJSON에_매핑된_법정동이_누락되면_저장하지_않는다()
		throws IOException {
		Path mappingPath = tempDirectory.resolve(
			"missing-boundary-mapping.json"
		);
		Path boundaryPath = tempDirectory.resolve(
			"missing-boundary.geojson"
		);

		Files.writeString(mappingPath, """
			{
			  "regions": [
			    {
			      "code": "missing-boundary-test-region",
			      "name": "누락 검증 지역",
			      "active": true,
			      "displayOrder": 999
			    }
			  ],
			  "cities": [
			    {
			      "id": "11710",
			      "regionCode": "missing-boundary-test-region",
			      "name": "송파구"
			    }
			  ],
			  "zones": [
			    {
			      "id": "missing-boundary-test-zone",
			      "cityId": "11710",
			      "name": "누락 검증 권역",
			      "dongCodes": [
			        "1171010100",
			        "1171010200"
			      ],
			      "initialTier": 2.7
			    }
			  ]
			}
			""");

		Files.writeString(boundaryPath, """
			{
			  "type": "FeatureCollection",
			  "features": [
			    {
			      "type": "Feature",
			      "properties": {
			        "EMD_CD": "1171010100"
			      },
			      "geometry": {
			        "type": "Polygon",
			        "coordinates": [
			          [
			            [127.10, 37.50],
			            [127.11, 37.50],
			            [127.11, 37.51],
			            [127.10, 37.51],
			            [127.10, 37.50]
			          ]
			        ]
			      }
			    }
			  ]
			}
			""");

		assertThatThrownBy(() -> service.importLocationData(
			mappingPath,
			boundaryPath,
			"EMD_CD"
		))
			.isInstanceOf(LocationMappingException.class)
			.hasMessageContaining("1171010200");

		Integer importedRegionCount = jdbcTemplate.queryForObject(
			"""
				SELECT COUNT(*)
				FROM region
				WHERE code = ?
				""",
			Integer.class,
			"missing-boundary-test-region"
		);

		assertThat(importedRegionCount).isZero();
	}
}
