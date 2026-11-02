package com.geupjido.zone.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 경계가 맞닿은 권역 쌍을 인접 관계의 후보로 조회한다.
 */
@Repository
public class ZoneAdjacencyCandidateRepository {

	private final JdbcTemplate jdbcTemplate;

	public ZoneAdjacencyCandidateRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<ZoneIdPair> findTouchingPairs() {
		return jdbcTemplate.query("""
			SELECT first_zone.id AS zone_id, second_zone.id AS adjacent_zone_id
			FROM zone first_zone
			JOIN zone second_zone
				ON first_zone.id < second_zone.id
				AND first_zone.polygon && second_zone.polygon
				AND ST_Touches(first_zone.polygon, second_zone.polygon)
			ORDER BY first_zone.id, second_zone.id
			""", (resultSet, rowNumber) -> new ZoneIdPair(
			resultSet.getString("zone_id"),
			resultSet.getString("adjacent_zone_id")
		));
	}

	public record ZoneIdPair(String zoneId, String adjacentZoneId) {
	}
}
