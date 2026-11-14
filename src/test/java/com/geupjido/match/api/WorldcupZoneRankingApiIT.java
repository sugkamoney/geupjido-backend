package com.geupjido.match.api;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupGameRepository;
import com.geupjido.match.service.MatchGenerationService;
import com.geupjido.match.service.WorldcupVoteRatingPublicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorldcupZoneRankingApiIT {

	@Autowired private MockMvc mockMvc;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private MatchCandidateRepository candidateRepository;
	@Autowired private MatchGenerationService generator;
	@Autowired private WorldcupGameRepository gameRepository;
	@Autowired private WorldcupVoteRatingPublicationService publicationService;

	@Test
	void 초기_기준과_마지막_완료_시각을_조회한다() throws Exception {
		String zoneId = candidateRepository.findActiveZones().getFirst().zoneId();
		jdbcTemplate.update("UPDATE zone SET ranking_basis = 'INITIAL' WHERE id = ?", zoneId);

		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/ranking", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.zoneId").value(zoneId))
			.andExpect(jsonPath("$.rankingBasis").value("INITIAL"))
			.andExpect(jsonPath("$.tier").isNumber())
			.andExpect(jsonPath("$.complexes.length()").isNumber());
		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/ranking", "unknown-zone"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("ZONE_NOT_FOUND"));
	}

	@Test
	void 중단한_게임의_표도_누적해_급지와_순위를_함께_발행한다() throws Exception {
		String zoneId = candidateRepository.findActiveZones().stream()
			.filter(zone -> zone.activeComplexCount() >= 4)
			.findFirst().orElseThrow().zoneId();
		for (int visitorIndex = 0; visitorIndex < 10; visitorIndex++) {
			var game = generator.generateForZone(zoneId);
			long gameId = gameRepository.save(UUID.randomUUID(), game, Instant.now()).id();
			for (int index = 0; index < 9; index++) {
				var matchup = game.matchups().get(index);
				gameRepository.saveVote(gameId, index + 1,
					matchup.left().complexId(), matchup.right().complexId(),
					matchup.left().complexId());
			}
		}

		Instant completedAt = publicationService.publish();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT valid_vote_count FROM worldcup_rating_publication WHERE id = 1",
			Long.class)).isPositive();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT participant_count FROM worldcup_rating_publication WHERE id = 1",
			Long.class)).isGreaterThanOrEqualTo(10L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM worldcup_zone_vote_status WHERE valid_vote_count > 0",
			Long.class)).isPositive();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT ranking_basis FROM zone WHERE id = ?", String.class, zoneId))
			.isEqualTo("VOTE");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM complex WHERE zone_id = ? AND score IS NOT NULL "
				+ "AND zone_rank IS NOT NULL", Integer.class, zoneId)).isPositive();
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM complex WHERE vote_initial_rating IS NOT NULL",
			Integer.class)).isPositive();
		String complexId = jdbcTemplate.queryForObject(
			"SELECT id FROM complex WHERE zone_id = ? AND vote_initial_rating IS NOT NULL "
				+ "ORDER BY id LIMIT 1", String.class, zoneId);
		var firstScore = jdbcTemplate.queryForObject(
			"SELECT score FROM complex WHERE id = ?", java.math.BigDecimal.class,
			complexId);
		var firstTier = jdbcTemplate.queryForObject(
			"SELECT tier FROM zone WHERE id = ?", java.math.BigDecimal.class,
			zoneId);
		jdbcTemplate.update("UPDATE complex SET price = price + 1000 WHERE id = ?", complexId);
		Instant secondCompletedAt = publicationService.publish();
		assertThat(secondCompletedAt).isAfterOrEqualTo(completedAt);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT score FROM complex WHERE id = ?", java.math.BigDecimal.class,
			complexId)).isEqualByComparingTo(firstScore);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT tier FROM zone WHERE id = ?", java.math.BigDecimal.class,
			zoneId)).isEqualByComparingTo(firstTier);

		mockMvc.perform(get("/api/v1/worldcup/zones/{zoneId}/ranking", zoneId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rankingBasis").value("VOTE"))
			.andExpect(jsonPath("$.lastCompletedAt").value(secondCompletedAt.toString()))
			.andExpect(jsonPath("$.complexes[0].zoneRank").isNumber())
			.andExpect(jsonPath("$.complexes[0].score").isNumber());
	}
}
