package com.geupjido.zone.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.zone.domain.ZoneAdjacencySource;
import com.geupjido.zone.repository.ZoneAdjacencyCandidateRepository.ZoneIdPair;

/**
 * 맞닿은 권역 후보를 양방향 인접 관계로 저장한다.
 */
@Repository
public class ZoneAdjacencyImportRepository {

	private final JdbcTemplate jdbcTemplate;

	public ZoneAdjacencyImportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void insertAutoTouchPairs(List<ZoneIdPair> pairs) {
		if (pairs.isEmpty()) {
			return;
		}

		List<Object[]> rows = new ArrayList<>(pairs.size() * 2);
		for (ZoneIdPair pair : pairs) {
			rows.add(new Object[] {pair.zoneId(), pair.adjacentZoneId(), ZoneAdjacencySource.AUTO_TOUCH.getDatabaseValue()});
			rows.add(new Object[] {pair.adjacentZoneId(), pair.zoneId(), ZoneAdjacencySource.AUTO_TOUCH.getDatabaseValue()});
		}

		jdbcTemplate.batchUpdate("""
			INSERT INTO zone_adjacency (zone_id, adjacent_zone_id, source)
			VALUES (?, ?, ?)
			ON CONFLICT (zone_id, adjacent_zone_id) DO NOTHING
			""", rows);
	}

	public void deleteStaleAutoTouchRelations() {
		jdbcTemplate.update("""
			DELETE FROM zone_adjacency adjacency
			WHERE adjacency.source = ?
				AND NOT EXISTS (
					SELECT 1
					FROM zone first_zone
					JOIN zone second_zone ON second_zone.id = adjacency.adjacent_zone_id
					WHERE first_zone.id = adjacency.zone_id
						AND first_zone.polygon && second_zone.polygon
						AND ST_Touches(first_zone.polygon, second_zone.polygon)
				)
			""", ZoneAdjacencySource.AUTO_TOUCH.getDatabaseValue());
	}
}
