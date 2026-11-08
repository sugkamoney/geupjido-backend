package com.geupjido.match.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupGameRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
class WorldcupGameStartPersistenceIT {

	@Autowired
	private MatchCandidateRepository candidateRepository;

	@Autowired
	private MatchGenerationService generator;

	@Autowired
	private WorldcupGameStartPersistenceService startPersistenceService;

	@Autowired
	private WorldcupGameRepository gameRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void 같은_방문자가_동시에_시작해도_미완료_게임은_하나만_저장한다() throws Exception {
		List<MatchZoneCandidate> zones = candidateRepository.findActiveZones();
		GeneratedMatchGame game = generator.generateForZone(zones.getFirst().zoneId());
		UUID anonymousId = UUID.randomUUID();
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			Future<WorldcupGameStartResult> first = executor.submit(() -> {
				start.await();
				return startPersistenceService.saveOrResume(anonymousId, game);
			});
			Future<WorldcupGameStartResult> second = executor.submit(() -> {
				start.await();
				return startPersistenceService.saveOrResume(anonymousId, game);
			});
			start.countDown();
			WorldcupGameStartResult firstResult = first.get(20, TimeUnit.SECONDS);
			WorldcupGameStartResult secondResult = second.get(20, TimeUnit.SECONDS);
			assertThat(firstResult.state().id()).isEqualTo(secondResult.state().id());
			assertThat(List.of(firstResult.created(), secondResult.created()))
				.containsExactlyInAnyOrder(true, false);
			assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
				Integer.class, anonymousId
			)).isEqualTo(1);
		} finally {
			jdbcTemplate.update("DELETE FROM worldcup_game WHERE anonymous_id = ?", anonymousId);
		}
	}

	@Test
	void 여섯번째_동시_시작은_한_게임만_생성하고_일곱번째는_거부한다() throws Exception {
		GeneratedMatchGame game = generator.generateForZone(
			candidateRepository.findActiveZones().getFirst().zoneId());
		UUID anonymousId = UUID.randomUUID();
		try {
			for (int i = 0; i < 5; i++) {
				gameRepository.complete(gameRepository.save(anonymousId, game, Instant.now()).id());
			}
			CountDownLatch start = new CountDownLatch(1);
			try (var executor = Executors.newFixedThreadPool(2)) {
				Future<WorldcupGameStartResult> first = executor.submit(() -> {
					start.await();
					return startPersistenceService.saveOrResume(anonymousId, game);
				});
				Future<WorldcupGameStartResult> second = executor.submit(() -> {
					start.await();
					return startPersistenceService.saveOrResume(anonymousId, game);
				});
				start.countDown();
				WorldcupGameStartResult firstResult = first.get(20, TimeUnit.SECONDS);
				WorldcupGameStartResult secondResult = second.get(20, TimeUnit.SECONDS);
				assertThat(firstResult.state().id()).isEqualTo(secondResult.state().id());
				assertThat(List.of(firstResult.created(), secondResult.created()))
					.containsExactlyInAnyOrder(true, false);
				assertThat(jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM worldcup_game WHERE anonymous_id = ?",
					Integer.class, anonymousId
				)).isEqualTo(6);
				gameRepository.complete(firstResult.state().id());
				assertThatThrownBy(() -> startPersistenceService.saveOrResume(anonymousId, game))
					.isInstanceOf(WorldcupDailyLimitReachedException.class);
			}
		} finally {
			jdbcTemplate.update("DELETE FROM worldcup_game WHERE anonymous_id = ?", anonymousId);
		}
	}

	@Test
	void 한국_날짜_시작과_끝_경계에_있는_게임만_당일_횟수에_포함한다() {
		GeneratedMatchGame game = generator.generateForZone(
			candidateRepository.findActiveZones().getFirst().zoneId());
		UUID anonymousId = UUID.randomUUID();
		ZoneId korea = ZoneId.of("Asia/Seoul");
		LocalDate today = LocalDate.now(korea);
		Instant from = today.atStartOfDay(korea).toInstant();
		Instant to = today.plusDays(1).atStartOfDay(korea).toInstant();
		try {
			gameRepository.save(anonymousId, game, from.minusSeconds(1));
			gameRepository.save(anonymousId, game, from);
			gameRepository.save(anonymousId, game, to);

			assertThat(gameRepository.countStartedByAnonymousId(anonymousId, from, to)).isEqualTo(1);
		} finally {
			jdbcTemplate.update("DELETE FROM worldcup_game WHERE anonymous_id = ?", anonymousId);
		}
	}
}
