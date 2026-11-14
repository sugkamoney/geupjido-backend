package com.geupjido.match.repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.geupjido.match.model.WorldcupTimelineVote;
import com.geupjido.match.model.WorldcupTimelineVote.Candidate;

@Repository
public class JdbcWorldcupTimelineRepository implements WorldcupTimelineRepository {

	private static final String RECENT_VOTES_SQL = """
		SELECT game.anonymous_id, vote.selected_at, vote.selected_complex_id,
		       vote.left_complex_id, vote.right_complex_id,
		       vote.left_zone_id, vote.right_zone_id,
		       left_zone.name AS left_zone_name,
		       right_zone.name AS right_zone_name,
		       game.matchups -> (vote.round_number - 1) -> 'left' ->> 'name'
		           AS left_complex_name,
		       game.matchups -> (vote.round_number - 1) -> 'right' ->> 'name'
		           AS right_complex_name
		FROM worldcup_vote AS vote
		JOIN worldcup_game AS game ON game.id = vote.game_id
		JOIN zone AS left_zone ON left_zone.id = vote.left_zone_id
		JOIN zone AS right_zone ON right_zone.id = vote.right_zone_id
		WHERE (:zoneId IS NULL OR vote.left_zone_id = :zoneId
		       OR vote.right_zone_id = :zoneId)
		ORDER BY vote.selected_at DESC, vote.game_id DESC, vote.round_number DESC
		LIMIT :limit
		""";

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public JdbcWorldcupTimelineRepository(NamedParameterJdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<WorldcupTimelineVote> recentVotes(String zoneId, int limit) {
		return jdbcTemplate.query(RECENT_VOTES_SQL,
			new MapSqlParameterSource()
				.addValue("zoneId", zoneId, Types.VARCHAR)
				.addValue("limit", limit),
			(rs, rowNum) -> new WorldcupTimelineVote(
				rs.getObject("anonymous_id", UUID.class),
				rs.getObject("selected_at", OffsetDateTime.class).toInstant(),
				new Candidate(rs.getString("left_complex_id"),
					rs.getString("left_complex_name"),
					rs.getString("left_zone_id"), rs.getString("left_zone_name")),
				new Candidate(rs.getString("right_complex_id"),
					rs.getString("right_complex_name"),
					rs.getString("right_zone_id"), rs.getString("right_zone_name")),
				rs.getString("selected_complex_id")
			));
	}
}
