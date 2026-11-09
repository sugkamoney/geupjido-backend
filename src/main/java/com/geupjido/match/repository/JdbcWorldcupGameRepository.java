package com.geupjido.match.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.RecordedSkip;
import com.geupjido.match.model.WorldcupGameState;

@Repository
public class JdbcWorldcupGameRepository implements WorldcupGameRepository {

	private static final String INSERT_SQL = """
		INSERT INTO worldcup_game (anonymous_id, base_zone_id, tier_range_used, matchups, started_at)
		VALUES (:anonymousId, :baseZoneId, :tierRangeUsed, CAST(:matchups AS jsonb), :startedAt)
		RETURNING id
		""";

	private static final String FIND_LATEST_SQL = """
		SELECT id, base_zone_id, tier_range_used, matchups, completed_at
		FROM worldcup_game
		WHERE anonymous_id = :anonymousId
		ORDER BY id DESC
		LIMIT 1
		""";

	private static final String FIND_VOTES_SQL = """
		SELECT round_number, selected_complex_id, selected_at
		FROM worldcup_vote
		WHERE game_id = :gameId
		ORDER BY round_number
		""";

	private static final String COUNT_STARTED_SQL = """
		SELECT COUNT(*)
		FROM worldcup_game
		WHERE anonymous_id = :anonymousId
		  AND started_at >= :fromInclusive
		  AND started_at < :toExclusive
		""";

	private static final String FIND_SKIPS_SQL = """
		SELECT round_number, skipped_at
		FROM worldcup_skip
		WHERE game_id = :gameId
		ORDER BY round_number
		""";

	private static final String INSERT_VOTE_SQL = """
		INSERT INTO worldcup_vote
		    (game_id, round_number, left_complex_id, right_complex_id, selected_complex_id,
		     selected_at, left_zone_id, right_zone_id)
		SELECT :gameId, :roundNumber, :leftComplexId, :rightComplexId, :selectedComplexId,
		       GREATEST(clock_timestamp(), game.started_at), left_complex.zone_id, right_complex.zone_id
		FROM worldcup_game AS game
		JOIN complex AS left_complex
		    ON left_complex.id = :leftComplexId AND left_complex.zone_id IS NOT NULL
		JOIN complex AS right_complex
		    ON right_complex.id = :rightComplexId AND right_complex.zone_id IS NOT NULL
		WHERE game.id = :gameId
		ON CONFLICT (game_id, round_number) DO NOTHING
		""";

	private static final TypeReference<List<Matchup>> MATCHUPS_TYPE = new TypeReference<>() {
	};

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public JdbcWorldcupGameRepository(
		NamedParameterJdbcTemplate jdbcTemplate,
		ObjectMapper objectMapper
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public void lockAnonymousId(UUID anonymousId) {
		jdbcTemplate.query(
			"SELECT pg_advisory_xact_lock(hashtextextended(CAST(:anonymousId AS text), 0))",
			new MapSqlParameterSource("anonymousId", anonymousId),
			rs -> {
				rs.next();
				return null;
			}
		);
	}

	@Override
	public WorldcupGameState save(UUID anonymousId, GeneratedMatchGame game, Instant startedAt) {
		try {
			String matchupsJson = objectMapper.writeValueAsString(game.matchups());
			Long id = jdbcTemplate.queryForObject(
				INSERT_SQL,
				new MapSqlParameterSource()
					.addValue("anonymousId", anonymousId)
					.addValue("baseZoneId", game.baseZoneId())
					.addValue("tierRangeUsed", game.tierRangeUsed())
					.addValue("matchups", matchupsJson)
					.addValue("startedAt", OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC)),
				Long.class
			);
			if (id == null) {
				throw new DataRetrievalFailureException("저장한 월드컵 게임 번호를 읽을 수 없습니다.");
			}
			return new WorldcupGameState(id, game, List.of(), List.of(), null);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("월드컵 대진을 저장할 수 없습니다.", exception);
		}
	}

	@Override
	public Optional<WorldcupGameState> findLatestByAnonymousId(UUID anonymousId) {
		return findLatest(anonymousId, FIND_LATEST_SQL);
	}

	@Override
	public Optional<WorldcupGameState> findLatestByAnonymousIdForUpdate(UUID anonymousId) {
		return findLatest(anonymousId, FIND_LATEST_SQL + "FOR UPDATE");
	}

	@Override
	public long countStartedByAnonymousId(UUID anonymousId, Instant fromInclusive, Instant toExclusive) {
		Long count = jdbcTemplate.queryForObject(
			COUNT_STARTED_SQL,
			new MapSqlParameterSource()
				.addValue("anonymousId", anonymousId)
				.addValue("fromInclusive", OffsetDateTime.ofInstant(fromInclusive, ZoneOffset.UTC))
				.addValue("toExclusive", OffsetDateTime.ofInstant(toExclusive, ZoneOffset.UTC)),
			Long.class
		);
		return count == null ? 0 : count;
	}

	@Override
	public void saveVote(long gameId, int roundNumber, String leftComplexId,
		String rightComplexId, String selectedComplexId) {
		int inserted = jdbcTemplate.update(
			INSERT_VOTE_SQL,
			new MapSqlParameterSource()
				.addValue("gameId", gameId)
				.addValue("roundNumber", roundNumber)
				.addValue("leftComplexId", leftComplexId)
				.addValue("rightComplexId", rightComplexId)
				.addValue("selectedComplexId", selectedComplexId)
		);
		if (inserted != 1) {
			throw new IllegalStateException("이미 저장된 판의 선택을 다시 기록할 수 없습니다.");
		}
	}

	@Override
	public void saveSkip(long gameId, int roundNumber) {
		int inserted = jdbcTemplate.update(
			"INSERT INTO worldcup_skip (game_id, round_number, skipped_at) "
				+ "SELECT id, :roundNumber, GREATEST(clock_timestamp(), started_at) "
				+ "FROM worldcup_game WHERE id = :gameId",
			new MapSqlParameterSource("gameId", gameId)
				.addValue("roundNumber", roundNumber)
		);
		if (inserted != 1) {
			throw new IllegalStateException("건너뛴 판을 저장할 수 없습니다.");
		}
	}

	@Override
	public void complete(long gameId) {
		int updated = jdbcTemplate.update(
			"UPDATE worldcup_game SET completed_at = GREATEST(clock_timestamp(), started_at) "
				+ "WHERE id = :gameId AND completed_at IS NULL",
			new MapSqlParameterSource("gameId", gameId)
		);
		if (updated != 1) {
			throw new IllegalStateException("월드컵 게임을 완료할 수 없습니다.");
		}
	}

	private Optional<WorldcupGameState> findLatest(UUID anonymousId, String sql) {
		return jdbcTemplate.query(
			sql,
			new MapSqlParameterSource("anonymousId", anonymousId),
			(rs, rowNum) -> {
				long id = rs.getLong("id");
				OffsetDateTime completedAt = rs.getObject("completed_at", OffsetDateTime.class);
				return new WorldcupGameState(
					id,
					new GeneratedMatchGame(
						rs.getString("base_zone_id"),
						rs.getObject("tier_range_used", BigDecimal.class),
						readMatchups(rs.getString("matchups"))
					),
					findVotes(id),
					findSkips(id),
					completedAt == null ? null : completedAt.toInstant()
				);
			}
		).stream().findFirst();
	}

	private List<RecordedVote> findVotes(long gameId) {
		return jdbcTemplate.query(
			FIND_VOTES_SQL,
			new MapSqlParameterSource("gameId", gameId),
			(rs, rowNum) -> new RecordedVote(
				rs.getInt("round_number"),
				rs.getString("selected_complex_id"),
				rs.getObject("selected_at", OffsetDateTime.class).toInstant()
			)
		);
	}

	private List<RecordedSkip> findSkips(long gameId) {
		return jdbcTemplate.query(
			FIND_SKIPS_SQL,
			new MapSqlParameterSource("gameId", gameId),
			(rs, rowNum) -> new RecordedSkip(
				rs.getInt("round_number"),
				rs.getObject("skipped_at", OffsetDateTime.class).toInstant()
			)
		);
	}

	private List<Matchup> readMatchups(String json) {
		try {
			return objectMapper.readValue(json, MATCHUPS_TYPE);
		} catch (JsonProcessingException exception) {
			throw new DataRetrievalFailureException("저장된 월드컵 대진을 읽을 수 없습니다.", exception);
		}
	}
}
