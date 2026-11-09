package com.geupjido.match.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.model.WorldcupZoneRanking;
import com.geupjido.match.model.WorldcupZoneRanking.ComplexRank;
import com.geupjido.zone.domain.RankingBasis;

@Repository
public class JdbcWorldcupZoneRankingRepository implements WorldcupZoneRankingRepository {

	private static final String QUERY = """
		SELECT z.id AS zone_id, z.name AS zone_name, z.tier, z.ranking_basis,
		       p.last_completed_at, c.id AS complex_id, c.name AS complex_name,
		       c.zone_rank, c.score, c.price, c.representative_area
		FROM zone z
		CROSS JOIN worldcup_rating_publication p
		LEFT JOIN complex c ON c.zone_id = z.id AND c.is_active = TRUE
		WHERE z.id = ? AND p.id = 1
		ORDER BY c.zone_rank NULLS LAST, c.id
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcWorldcupZoneRankingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<WorldcupZoneRanking> findByZoneId(String zoneId) {
		return jdbcTemplate.query(QUERY, resultSet -> {
			if (!resultSet.next()) {
				return Optional.empty();
			}
			String name = resultSet.getString("zone_name");
			var tier = resultSet.getBigDecimal("tier");
			var basis = RankingBasis.valueOf(resultSet.getString("ranking_basis"));
			OffsetDateTime completedAt = resultSet.getObject(
				"last_completed_at", OffsetDateTime.class);
			var complexes = new ArrayList<ComplexRank>();
			do {
				String complexId = resultSet.getString("complex_id");
				if (complexId != null) {
					complexes.add(new ComplexRank(
						complexId, resultSet.getString("complex_name"),
						resultSet.getObject("zone_rank", Integer.class),
						resultSet.getBigDecimal("score"),
						resultSet.getObject("price", Integer.class),
						resultSet.getBigDecimal("representative_area")
					));
				}
			} while (resultSet.next());
			return Optional.of(new WorldcupZoneRanking(
				zoneId, name, tier, basis,
				completedAt == null ? null : completedAt.toInstant(), complexes
			));
		}, zoneId);
	}
}
