package com.geupjido.ranking.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.ranking.model.RankingSource;
import com.geupjido.ranking.model.RankingView.Region;
import com.geupjido.zone.domain.RankingBasis;

@Repository
public class JdbcRankingRepository implements RankingRepository {
	private static final String ZONES = """
		SELECT z.id, z.name, c.name AS city_name, r.name AS region_name,
		       z.initial_tier, z.tier, z.score, z.ranking_basis,
		       z.previous_published_tier,
		       COALESCE(s.valid_vote_count, 0) AS valid_vote_count,
		       COALESCE(s.participant_count, 0) AS participant_count,
		       COALESCE(s.cross_zone_vote_count, 0) AS cross_zone_vote_count
		FROM zone z
		JOIN city c ON c.id = z.city_id
		JOIN region r ON r.code = c.region_code AND r.is_active = TRUE
		LEFT JOIN worldcup_zone_vote_status s ON s.zone_id = z.id
		WHERE 1 = 1
		""";

	private final JdbcTemplate jdbcTemplate;

	public JdbcRankingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public RankingSource.Publication publication() {
		return jdbcTemplate.queryForObject("""
			SELECT last_completed_at, previous_completed_at
			FROM worldcup_rating_publication WHERE id = 1
			""", (rs, rowNum) -> {
			OffsetDateTime last = rs.getObject("last_completed_at", OffsetDateTime.class);
			OffsetDateTime previous = rs.getObject("previous_completed_at",
				OffsetDateTime.class);
			return new RankingSource.Publication(last == null ? null : last.toInstant(),
				previous == null ? null : previous.toInstant());
		});
	}

	@Override
	public List<RankingSource.Zone> zones(Region region) {
		String regionClause = switch (region) {
			case ALL -> "";
			case SEOUL -> " AND c.region_code = 'seoul'";
			case GYEONGGI_INCHEON ->
				" AND c.region_code IN ('gyeonggi', 'incheon')";
		};
		return jdbcTemplate.query(ZONES + regionClause + " ORDER BY z.id",
			(rs, rowNum) -> new RankingSource.Zone(
				rs.getString("id"), rs.getString("name"),
				rs.getString("city_name"), rs.getString("region_name"),
				rs.getBigDecimal("initial_tier"), rs.getBigDecimal("tier"),
				rs.getBigDecimal("score"),
				RankingBasis.valueOf(rs.getString("ranking_basis")),
				rs.getBigDecimal("previous_published_tier"),
				rs.getInt("valid_vote_count"), rs.getInt("participant_count"),
				rs.getInt("cross_zone_vote_count")));
	}
}
