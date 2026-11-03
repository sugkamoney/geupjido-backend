package com.geupjido.match.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchZoneCandidate;

@Repository
public class JdbcMatchCandidateRepository implements MatchCandidateRepository {

	// 활성 단지가 있는 권역만 후보로 조회한다. 활성 단지 수는 배너 진입 가능 여부를 판단할 때 사용한다.
	private static final String ACTIVE_ZONES_SQL = """
		SELECT z.id AS zone_id, z.city_id, z.name AS zone_name, z.tier,
		       ST_X(z.center) AS longitude, ST_Y(z.center) AS latitude,
		       active_complexes.active_count
		FROM zone z
		JOIN (
		    SELECT zone_id, COUNT(*) AS active_count
		    FROM complex
		    WHERE is_active = TRUE AND zone_id IS NOT NULL
		    GROUP BY zone_id
		) active_complexes ON active_complexes.zone_id = z.id
		WHERE z.tier IS NOT NULL
		ORDER BY z.id
		""";

	// 선택된 권역의 활성 단지와 대진 카드에 표시할 대표 거래 정보를 함께 가져온다.
	private static final String ACTIVE_COMPLEXES_SQL = """
		SELECT c.id AS complex_id, c.zone_id, c.name, city.name AS city_name,
		       z.name AS zone_name, z.tier AS zone_tier, c.households,
		       c.approval_date, c.representative_area, c.price,
		       c.last_deal_date
		FROM complex c
		JOIN zone z ON z.id = c.zone_id
		JOIN city ON city.id = z.city_id
		WHERE c.is_active = TRUE AND z.tier IS NOT NULL
		  AND c.zone_id IN (:zoneIds)
		ORDER BY c.zone_id, c.id
		""";

	private static final RowMapper<MatchZoneCandidate> ZONE_MAPPER =
		(rs, rowNum) -> new MatchZoneCandidate(
			rs.getString("zone_id"),
			rs.getString("city_id"),
			rs.getString("zone_name"),
			rs.getBigDecimal("tier"),
			rs.getDouble("longitude"),
			rs.getDouble("latitude"),
			rs.getLong("active_count")
		);

	private static final RowMapper<MatchComplexCandidate> COMPLEX_MAPPER =
		(rs, rowNum) -> mapComplex(rs);

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcMatchCandidateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<MatchZoneCandidate> findActiveZones() {
		return jdbcTemplate.query(ACTIVE_ZONES_SQL, ZONE_MAPPER);
	}

	@Override
	public List<MatchComplexCandidate> findActiveComplexesByZoneIds(Collection<String> zoneIds) {
		// 빈 목록으로 IN 절을 실행하지 않고 후보가 없음을 바로 반환한다.
		if (zoneIds.isEmpty()) {
			return List.of();
		}
		return jdbcTemplate.query(
			ACTIVE_COMPLEXES_SQL,
			new MapSqlParameterSource("zoneIds", zoneIds),
			COMPLEX_MAPPER
		);
	}

	private static MatchComplexCandidate mapComplex(ResultSet rs) throws SQLException {
		return new MatchComplexCandidate(
			rs.getString("complex_id"),
			rs.getString("zone_id"),
			rs.getString("name"),
			rs.getString("city_name"),
			rs.getString("zone_name"),
			rs.getBigDecimal("zone_tier"),
			rs.getInt("households"),
			rs.getDate("approval_date").toLocalDate(),
			rs.getBigDecimal("representative_area"),
			rs.getInt("price"),
			rs.getDate("last_deal_date").toLocalDate()
		);
	}
}
