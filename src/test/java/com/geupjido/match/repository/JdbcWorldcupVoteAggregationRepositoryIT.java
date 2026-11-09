package com.geupjido.match.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.WorldcupVoteForAggregation;
import com.geupjido.match.service.MatchGenerationService;
import com.geupjido.match.service.WorldcupVoteAggregationInputService;
import com.geupjido.match.service.WorldcupVoteEligibilityPolicy;
import com.geupjido.match.service.WorldcupVoteRatingService;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JdbcWorldcupVoteAggregationRepositoryIT {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MatchCandidateRepository candidateRepository;

	@Autowired
	private WorldcupGameRepository gameRepository;

	@Autowired
	private WorldcupVoteAggregationRepository aggregationRepository;

	@Autowired
	private WorldcupVoteAggregationInputService aggregationInputService;

	@Autowired
	private WorldcupVoteRatingSeedRepository ratingSeedRepository;

	@Autowired
	private WorldcupVoteRatingService ratingService;

	@Autowired
	private MatchGenerationService generator;

	@Test
	void 공개_급지가_바뀌어도_초기_급지로_다시_계산한다() {
		var seeds = ratingSeedRepository.loadSeeds();
		assertThat(seeds.zones()).isNotEmpty();
		assertThat(seeds.complexes()).isNotEmpty();
		String zoneId = seeds.zones().getFirst().id();
		var initialTier = seeds.zones().getFirst().tier();
		var before = ratingService.calculate();
		jdbcTemplate.update("UPDATE zone SET tier = ? WHERE id = ?",
			initialTier.compareTo(new java.math.BigDecimal("1.0")) == 0
				? new java.math.BigDecimal("1.1") : new java.math.BigDecimal("1.0"), zoneId);
		var after = ratingService.calculate();
		assertThat(after.input().ratings().zoneScores().get(zoneId))
			.isEqualTo(before.input().ratings().zoneScores().get(zoneId));
		assertThat(after.input().ratings().complexScores())
			.isEqualTo(before.input().ratings().complexScores());
	}

	@Test
	void 선택을_저장할_때_권역을_고정하고_나중에_단지_권역이_바뀌어도_집계_권역은_유지한다() {
		String baseZoneId = candidateRepository.findActiveZones().getFirst().zoneId();
		var game = generator.generateForZone(baseZoneId);
		Matchup first = game.matchups().getFirst();
		String leftId = first.left().complexId();
		String rightId = first.right().complexId();
		String originalLeftZone = jdbcTemplate.queryForObject(
			"SELECT zone_id FROM complex WHERE id = ?", String.class, leftId);
		String originalRightZone = jdbcTemplate.queryForObject(
			"SELECT zone_id FROM complex WHERE id = ?", String.class, rightId);
		String otherZone = candidateRepository.findActiveZones().stream()
			.map(zone -> zone.zoneId())
			.filter(zoneId -> !zoneId.equals(originalLeftZone))
			.findFirst().orElseThrow();
		var before = aggregationInputService.forEachValidVote(ignored -> { });
		UUID visitor = UUID.randomUUID();
		long gameId = gameRepository.save(visitor, game, Instant.now()).id();
		gameRepository.saveVote(gameId, 1, leftId, rightId, leftId);
		assertThat(gameRepository.findLatestByAnonymousId(visitor).orElseThrow().completed())
			.isFalse();

		jdbcTemplate.update("UPDATE complex SET zone_id = ? WHERE id = ?", otherZone, leftId);
		List<WorldcupVoteForAggregation> recorded = new ArrayList<>();
		aggregationRepository.forEachRecordedVote(recorded::add);
		WorldcupVoteForAggregation vote = recorded.stream()
			.filter(item -> item.gameId() == gameId)
			.findFirst().orElseThrow();

		assertThat(vote.leftZoneId()).isEqualTo(originalLeftZone);
		assertThat(vote.rightZoneId()).isEqualTo(originalRightZone);
		assertThat(vote.selectedComplexId()).isEqualTo(leftId);
		List<WorldcupVoteForAggregation> valid = new ArrayList<>();
		var after = aggregationInputService.forEachValidVote(valid::add);
		int beforeCount = before.getOrDefault(originalLeftZone,
			new WorldcupVoteEligibilityPolicy.ZoneCount(0, 0, 0)).validVotes();
		assertThat(after.get(originalLeftZone).validVotes()).isEqualTo(beforeCount + 1);
		assertThat(valid).contains(vote);
	}
}
