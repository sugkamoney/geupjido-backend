package com.geupjido.batch.location.repository;

import com.geupjido.batch.location.model.LegalDongBoundary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;


import java.util.List;

/**
 * 법정동 경계를 PostGIS 임시 테이블에 저장하고 공간 연산을 수행한다.
 */
@Repository
public class LegalDongBoundaryRepository {

	private final JdbcTemplate jdbcTemplate;

	public LegalDongBoundaryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final String INSERT_BOUNDARY_SQL = """
		INSERT INTO legal_dong_boundary_stage
		(
			dong_code,
			geometry
		)
		VALUES
		(
			?,
			ST_Multi(
				ST_CollectionExtract(
					ST_MakeValid(
						ST_SetSRID(
							ST_GeomFromGeoJSON(?),
							4326
						)
					),
					3
				)
			)
		)
		""";

	public void createTemporaryTable() {
		jdbcTemplate.execute("""
			CREATE TEMPORARY TABLE legal_dong_boundary_stage
			(
				dong_code VARCHAR(10) PRIMARY KEY,
				geometry  GEOMETRY(MultiPolygon, 4326) NOT NULL,

				CONSTRAINT chk_legal_dong_boundary_not_empty
					CHECK (NOT ST_IsEmpty(geometry)),

				CONSTRAINT chk_legal_dong_boundary_valid
					CHECK (ST_IsValid(geometry))
			)
			ON COMMIT DROP
			""");
	}

	public void saveAll(List<LegalDongBoundary> boundaries) {
		jdbcTemplate.batchUpdate(
			INSERT_BOUNDARY_SQL,
			boundaries,
			100,
			(preparedStatement, boundary) -> {
				preparedStatement.setString(
					1,
					boundary.dongCode()
				);
				preparedStatement.setString(
					2,
					boundary.geometryJson()
				);
			}
		);
	}
}
