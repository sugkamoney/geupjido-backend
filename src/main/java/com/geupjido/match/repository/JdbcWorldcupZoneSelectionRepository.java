package com.geupjido.match.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorldcupZoneSelectionRepository implements WorldcupZoneSelectionRepository {

	private static final String ZONES_SQL = """
		SELECT z.id, city.name AS city_name, z.name AS zone_name, z.tier,
		       COALESCE(active_complexes.active_count, 0) AS active_count
		FROM zone z
		JOIN city ON city.id = z.city_id
		LEFT JOIN (
		    SELECT zone_id, COUNT(*) AS active_count
		    FROM complex
		    WHERE is_active = TRUE AND zone_id IS NOT NULL
		    GROUP BY zone_id
		) active_complexes ON active_complexes.zone_id = z.id
		ORDER BY city.name, z.name, z.id
		""";

	private static final String ADJACENT_SQL = """
		SELECT DISTINCT CASE WHEN zone_id = ? THEN adjacent_zone_id ELSE zone_id END
		FROM zone_adjacency
		WHERE zone_id = ? OR adjacent_zone_id = ?
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcWorldcupZoneSelectionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<ZoneRow> findAll() {
		return jdbcTemplate.query(ZONES_SQL, (rs, rowNum) -> new ZoneRow(
			rs.getString("id"),
			rs.getString("city_name"),
			rs.getString("zone_name"),
			rs.getBigDecimal("tier"),
			rs.getLong("active_count")
		));
	}

	@Override
	public List<String> findAdjacentZoneIds(String zoneId) {
		return jdbcTemplate.queryForList(ADJACENT_SQL, String.class, zoneId, zoneId, zoneId);
	}
}
