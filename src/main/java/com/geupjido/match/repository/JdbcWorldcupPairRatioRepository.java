package com.geupjido.match.repository;

import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.model.WorldcupPairRatio;

@Repository
public class JdbcWorldcupPairRatioRepository implements WorldcupPairRatioRepository {

	private static final String FIND_RATIO_SQL = """
		WITH pair_votes AS (
		    SELECT game.anonymous_id, vote.selected_complex_id,
		           ROW_NUMBER() OVER (
		               PARTITION BY game.anonymous_id,
		                            CAST(vote.selected_at AT TIME ZONE 'Asia/Seoul' AS date)
		               ORDER BY vote.selected_at, vote.game_id, vote.round_number
		           ) AS daily_order
		    FROM worldcup_vote AS vote
		    JOIN worldcup_game AS game ON game.id = vote.game_id
		    WHERE LEAST(vote.left_complex_id, vote.right_complex_id) = :firstComplexId
		      AND GREATEST(vote.left_complex_id, vote.right_complex_id) = :secondComplexId
		), valid_votes AS (
		    SELECT anonymous_id, selected_complex_id
		    FROM pair_votes
		    WHERE daily_order = 1
		)
		SELECT COUNT(*) FILTER (WHERE selected_complex_id = :leftComplexId) AS left_votes,
		       COUNT(*) AS total_votes,
		       COUNT(DISTINCT anonymous_id) AS distinct_visitors
		FROM valid_votes
		""";

	private static final long MIN_VISITORS = 10;

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcWorldcupPairRatioRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<WorldcupPairRatio> findVisibleRatio(String leftComplexId, String rightComplexId) {
		String first = leftComplexId.compareTo(rightComplexId) < 0 ? leftComplexId : rightComplexId;
		String second = leftComplexId.compareTo(rightComplexId) < 0 ? rightComplexId : leftComplexId;
		return jdbcTemplate.queryForObject(
			FIND_RATIO_SQL,
			new MapSqlParameterSource()
				.addValue("firstComplexId", first)
				.addValue("secondComplexId", second)
				.addValue("leftComplexId", leftComplexId),
			(rs, rowNum) -> {
				long visitors = rs.getLong("distinct_visitors");
				if (visitors < MIN_VISITORS) {
					return Optional.<WorldcupPairRatio>empty();
				}
				long total = rs.getLong("total_votes");
				long left = rs.getLong("left_votes");
				int leftPercent = (int) Math.round(left * 100.0 / total);
				return Optional.of(new WorldcupPairRatio(leftPercent, 100 - leftPercent));
			}
		);
	}
}
