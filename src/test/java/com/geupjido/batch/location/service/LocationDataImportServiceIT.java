package com.geupjido.batch.location.service;

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
	void 매핑_JSON과_GeoJSON을_읽어_지역_데이터를_저장한다()
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
			      "code": "seoul",
			      "name": "서울",
			      "active": true,
			      "displayOrder": 1
			    }
			  ],
			  "cities": [
			    {
			      "id": "11680",
			      "regionCode": "seoul",
			      "name": "강남구"
			    }
			  ],
			  "zones": [
			    {
			      "id": "integration-test-zone",
			      "cityId": "11680",
			      "name": "통합 테스트 권역",
			      "dongCodes": [
			        "1168011000"
			      ],
			       "initialTier": 1.1
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
			        "EMD_CD": "1168011000",
			        "EMD_NM": "압구정동"
			      },
			      "geometry": {
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
					AND z.tier = 1.1
					AND z.dong_codes = ARRAY[
						'1168011000'
					]::VARCHAR[]
					AND ST_GeometryType(z.polygon)
						= 'ST_MultiPolygon'
					AND ST_SRID(z.polygon) = 4326
					AND ST_Covers(z.polygon, z.center)
					AND c.name = '강남구'
					AND r.name = '서울'
				""",
			Integer.class,
			"integration-test-zone"
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
