package com.geupjido.match.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.WorldcupVoteForAggregation;

@Repository
public class JdbcWorldcupVoteAggregationRepository implements WorldcupVoteAggregationRepository {

	private static final String FIND_RECORDED_VOTES_SQL = """
		SELECT vote.game_id, vote.round_number, game.anonymous_id,
		       vote.left_complex_id, vote.right_complex_id, vote.selected_complex_id,
		       vote.left_zone_id, vote.right_zone_id, vote.selected_at
		FROM worldcup_vote AS vote
		JOIN worldcup_game AS game ON game.id = vote.game_id
		ORDER BY vote.selected_at, vote.game_id, vote.round_number
		""";

	private static final int FETCH_SIZE = 500;

	private final JdbcTemplate jdbcTemplate;

	public JdbcWorldcupVoteAggregationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	@Override
	public void forEachRecordedVote(Consumer<WorldcupVoteForAggregation> consumer) {
		RowCallbackHandler rowHandler = resultSet -> consumer.accept(new WorldcupVoteForAggregation(
			resultSet.getLong("game_id"),
			resultSet.getInt("round_number"),
			resultSet.getObject("anonymous_id", UUID.class),
			resultSet.getString("left_complex_id"),
			resultSet.getString("right_complex_id"),
			resultSet.getString("selected_complex_id"),
			resultSet.getString("left_zone_id"),
			resultSet.getString("right_zone_id"),
			resultSet.getObject("selected_at", OffsetDateTime.class).toInstant()
		));
		jdbcTemplate.query(
			FIND_RECORDED_VOTES_SQL,
			statement -> statement.setFetchSize(FETCH_SIZE),
			rowHandler
		);
	}
}
