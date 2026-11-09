package com.geupjido.match.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.WorldcupPairRatio;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class JdbcWorldcupPairRatioRepositoryIT {

	@Autowired
	private WorldcupPairRatioRepository ratioRepository;

	@Autowired
	private WorldcupGameRepository gameRepository;

	@Autowired
	private MatchCandidateRepository candidateRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 열명_미만은_숨기고_같은_날_중복과_좌우_반전을_같은_대진으로_계산한다() {
		String zoneId = candidateRepository.findActiveZones().getFirst().zoneId();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		String left = "ratio-l-" + suffix;
		String right = "ratio-r-" + suffix;
		MatchCard leftCard = new MatchCard(left, "왼쪽", "서울", "테스트", new BigDecimal("2.0"),
			100, 2000, null);
		MatchCard rightCard = new MatchCard(right, "오른쪽", "서울", "테스트", new BigDecimal("2.0"),
			100, 2000, null);
		Matchup matchup = new Matchup(MatchType.WITHIN_ZONE, leftCard, rightCard);
		GeneratedMatchGame game = new GeneratedMatchGame(zoneId, new BigDecimal("0.2"),
			IntStream.range(0, 10).mapToObj(ignored -> matchup).toList());
		Instant firstDay = Instant.parse("2026-11-01T03:00:00Z");
		UUID firstVisitor = null;
		for (int i = 0; i < 9; i++) {
			UUID visitor = UUID.randomUUID();
			if (i == 0) {
				firstVisitor = visitor;
			}
			insertVote(game, visitor, left, right, left, zoneId, firstDay.plusSeconds(i));
		}
		assertThat(ratioRepository.findVisibleRatio(left, right)).isEmpty();

		insertVote(game, UUID.randomUUID(), left, right, right, zoneId, firstDay.plusSeconds(9));
		assertThat(ratioRepository.findVisibleRatio(left, right))
			.contains(new WorldcupPairRatio(90, 10));
		assertThat(ratioRepository.findVisibleRatio(right, left))
			.contains(new WorldcupPairRatio(10, 90));

		insertVote(game, firstVisitor, right, left, right, zoneId, firstDay.plusSeconds(20));
		assertThat(ratioRepository.findVisibleRatio(left, right))
			.contains(new WorldcupPairRatio(90, 10));
	}

	private void insertVote(GeneratedMatchGame game, UUID visitor, String left, String right,
		String selected, String zoneId, Instant selectedAt) {
		long gameId = gameRepository.save(visitor, game, selectedAt).id();
		jdbcTemplate.update("""
			INSERT INTO worldcup_vote
			    (game_id, round_number, left_complex_id, right_complex_id, selected_complex_id,
			     selected_at, left_zone_id, right_zone_id)
			VALUES (?, 1, ?, ?, ?, ?, ?, ?)
			""", gameId, left, right, selected,
			OffsetDateTime.ofInstant(selectedAt, ZoneOffset.UTC), zoneId, zoneId);
	}
}
