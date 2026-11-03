package com.geupjido.match.repository;

import java.math.BigDecimal;
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

@Repository
public class JdbcWorldcupGameRepository implements WorldcupGameRepository {

	private static final String INSERT_SQL = """
		INSERT INTO worldcup_game (anonymous_id, base_zone_id, tier_range_used, matchups)
		VALUES (:anonymousId, :baseZoneId, :tierRangeUsed, CAST(:matchups AS jsonb))
		""";

	private static final String FIND_LATEST_SQL = """
		SELECT base_zone_id, tier_range_used, matchups
		FROM worldcup_game
		WHERE anonymous_id = :anonymousId
		ORDER BY id DESC
		LIMIT 1
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
	public void save(UUID anonymousId, GeneratedMatchGame game) {
		try {
			String matchupsJson = objectMapper.writeValueAsString(game.matchups());
			jdbcTemplate.update(
				INSERT_SQL,
				new MapSqlParameterSource()
					.addValue("anonymousId", anonymousId)
					.addValue("baseZoneId", game.baseZoneId())
					.addValue("tierRangeUsed", game.tierRangeUsed())
					.addValue("matchups", matchupsJson)
			);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("월드컵 대진을 저장할 수 없습니다.", exception);
		}
	}

	@Override
	public Optional<GeneratedMatchGame> findLatestByAnonymousId(UUID anonymousId) {
		return jdbcTemplate.query(
			FIND_LATEST_SQL,
			new MapSqlParameterSource("anonymousId", anonymousId),
			(rs, rowNum) -> new GeneratedMatchGame(
				rs.getString("base_zone_id"),
				rs.getObject("tier_range_used", BigDecimal.class),
				readMatchups(rs.getString("matchups"))
			)
		).stream().findFirst();
	}

	private List<Matchup> readMatchups(String json) {
		try {
			return objectMapper.readValue(json, MATCHUPS_TYPE);
		} catch (JsonProcessingException exception) {
			throw new DataRetrievalFailureException("저장된 월드컵 대진을 읽을 수 없습니다.", exception);
		}
	}
}
