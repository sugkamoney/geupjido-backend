package com.geupjido.batch.location.repository;

import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LocationMapping.CityDefinition;
import com.geupjido.batch.location.model.LocationMapping.RegionDefinition;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.List;

/**
 * 검증된 지역·시군구·권역 데이터를 최종 테이블에 저장한다.
 */
@Repository
public class LocationDataImportRepository {

	private static final String UPSERT_REGION_SQL = """
		INSERT INTO region
			(
				code,
				name,
				is_active,
				display_order
			)
			VALUES
			(
				?,
				?,
				?,
				?
			)
			ON CONFLICT (code)
			DO UPDATE SET
				name = EXCLUDED.name,
				is_active = EXCLUDED.is_active,
				display_order = EXCLUDED.display_order
		""";

	private static final String UPSERT_CITY_SQL = """
		WITH merged_boundary AS
		(
			SELECT ST_Multi(
				ST_Union(geometry)
			) AS polygon
			FROM legal_dong_boundary_stage
			WHERE LEFT(dong_code, 5) = ?
		)
		INSERT INTO city
		(
			id,
			region_code,
			name,
			polygon,
			center
		)
		SELECT
			?,
			?,
			?,
			polygon,
			ST_PointOnSurface(polygon)
		FROM merged_boundary
		WHERE polygon IS NOT NULL
			AND NOT ST_IsEmpty(polygon)
		ON CONFLICT (id)
		DO UPDATE SET
			region_code = EXCLUDED.region_code,
			name = EXCLUDED.name,
			polygon = EXCLUDED.polygon,
			center = EXCLUDED.center
		""";

	private static final String UPSERT_ZONE_SQL = """
		WITH merged_boundary AS
		(
			SELECT
				COUNT(*) AS boundary_count,
				ST_Multi(
					ST_Union(geometry)
				) AS polygon
			FROM legal_dong_boundary_stage
			WHERE dong_code = ANY (
				CAST(? AS VARCHAR[])
			)
		)
		INSERT INTO zone
		(
			id,
			city_id,
			name,
			dong_codes,
			polygon,
			center
		)
		SELECT
			?,
			?,
			?,
			CAST(? AS VARCHAR[]),
			polygon,
			ST_PointOnSurface(polygon)
		FROM merged_boundary
		WHERE boundary_count = ?
			AND polygon IS NOT NULL
			AND NOT ST_IsEmpty(polygon)
		ON CONFLICT (id)
		DO UPDATE SET
			city_id = EXCLUDED.city_id,
			name = EXCLUDED.name,
			dong_codes = EXCLUDED.dong_codes,
			polygon = EXCLUDED.polygon,
			center = EXCLUDED.center
		""";

	private final JdbcTemplate jdbcTemplate;

	public LocationDataImportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void upsertRegions(List<RegionDefinition> regions) {
		jdbcTemplate.batchUpdate(
			UPSERT_REGION_SQL,
			regions,
			100,
			(preparedStatement, region) -> {
				preparedStatement.setString(1, region.code());
				preparedStatement.setString(2, region.name());
				preparedStatement.setBoolean(3, region.active());
				preparedStatement.setInt(4, region.displayOrder());
			}
		);
	}

	public void upsertCities(List<CityDefinition> cities) {
		for (CityDefinition city : cities) {
			int affectedRows = jdbcTemplate.update(
				UPSERT_CITY_SQL,
				city.id(),
				city.id(),
				city.regionCode(),
				city.name()
			);
			if (affectedRows != 1) {
				throw new LocationMappingException(
					"city 경계를 생성할 법정동 데이터가 없습니다: "
						+ city.id()
				);
			}
		}
	}

	public void upsertZones(List<ZoneDefinition> zones) {
		for (ZoneDefinition zone : zones) {
			int affectedRows = jdbcTemplate.update(
				UPSERT_ZONE_SQL,
				preparedStatement -> {
					Array dongCodeArray = preparedStatement
						.getConnection()
						.createArrayOf(
							"varchar",
							zone.dongCodes().toArray(String[]::new)
						);

					preparedStatement.setArray(1, dongCodeArray);
					preparedStatement.setString(2, zone.id());
					preparedStatement.setString(3, zone.cityId());
					preparedStatement.setString(4, zone.name());
					preparedStatement.setArray(5, dongCodeArray);
					preparedStatement.setInt(
						6,
						zone.dongCodes().size()
					);
				}
			);

			if (affectedRows != 1) {
				throw new LocationMappingException(
					"zone 경계를 생성할 법정동 데이터가 누락되었습니다: "
						+ zone.id()
				);
			}
		}
	}
}
