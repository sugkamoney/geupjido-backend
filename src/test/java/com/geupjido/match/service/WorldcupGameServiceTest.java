package com.geupjido.match.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.WorldcupGameState;
import com.geupjido.match.repository.WorldcupGameRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class WorldcupGameServiceTest {

	private final MatchGenerationService generator = mock(MatchGenerationService.class);
	private final WorldcupGameRepository repository = mock(WorldcupGameRepository.class);
	private final WorldcupDailyGameLimit dailyGameLimit =
		new WorldcupDailyGameLimit(repository, Clock.systemUTC());
	private final WorldcupGameStartPersistenceService startPersistenceService =
		new WorldcupGameStartPersistenceService(repository, dailyGameLimit);
	private final WorldcupGameService service =
		new WorldcupGameService(generator, repository, startPersistenceService, dailyGameLimit);

	@Test
	void 생성된_게임을_익명_식별자와_함께_저장한다() {
		UUID anonymousId = UUID.randomUUID();
		GeneratedMatchGame game = new GeneratedMatchGame("zone", new BigDecimal("0.2"), List.of());
		when(generator.generateForZone("zone")).thenReturn(game);
		WorldcupGameState state = new WorldcupGameState(1L, game, List.of(), null);
		when(repository.save(eq(anonymousId), eq(game), any())).thenReturn(state);

		assertThat(service.startForZone(anonymousId, "zone"))
			.isEqualTo(new WorldcupGameStartResult(state, true));

		verify(repository).save(eq(anonymousId), eq(game), any());
	}

	@Test
	void 미완료_게임이_있으면_새_대진을_생성하지_않는다() {
		UUID anonymousId = UUID.randomUUID();
		WorldcupGameState state = new WorldcupGameState(1L,
			new GeneratedMatchGame("previous-zone", new BigDecimal("0.2"), List.of()),
			List.of(), null);
		when(repository.findLatestByAnonymousId(anonymousId))
			.thenReturn(Optional.of(state));

		assertThat(service.startForZone(anonymousId, "another-zone"))
			.isEqualTo(new WorldcupGameStartResult(state, false));
		verifyNoInteractions(generator);
	}

	@Test
	void 대진을_만들지_못하면_게임을_저장하지_않는다() {
		UUID anonymousId = UUID.randomUUID();
		when(generator.generateFromBanner())
			.thenThrow(new MatchUnavailableException("대진 부족"));

		assertThatThrownBy(() -> service.startFromBanner(anonymousId))
			.isInstanceOf(MatchUnavailableException.class);
		verify(repository).findLatestByAnonymousId(anonymousId);
	}

	@Test
	void 시작_한도에_도달하면_새_대진을_생성하지_않는다() {
		UUID anonymousId = UUID.randomUUID();
		when(repository.countStartedByAnonymousId(
			eq(anonymousId), any(), any()
		)).thenReturn(6L);

		assertThatThrownBy(() -> service.startFromBanner(anonymousId))
			.isInstanceOf(WorldcupDailyLimitReachedException.class);
		verifyNoInteractions(generator);
	}

	@Test
	void 현재_게임은_요청한_익명_식별자로만_조회한다() {
		UUID anonymousId = UUID.randomUUID();
		WorldcupGameState state = new WorldcupGameState(1L,
			new GeneratedMatchGame("zone", new BigDecimal("0.2"), List.of()), List.of(), null);
		when(repository.findLatestByAnonymousId(anonymousId)).thenReturn(Optional.of(state));

		assertThat(service.findCurrent(anonymousId)).isEqualTo(state);
		assertThatThrownBy(() -> service.findCurrent(UUID.randomUUID()))
			.isInstanceOf(WorldcupGameNotFoundException.class);
	}

	@Test
	void 이미_확정한_선택은_유지하고_다른_선택은_거부한다() {
		UUID anonymousId = UUID.randomUUID();
		GeneratedMatchGame game = gameWithOneMatchup();
		WorldcupGameState state = new WorldcupGameState(1L, game,
			List.of(new RecordedVote(1, "left", Instant.now())), null);
		when(repository.findLatestByAnonymousIdForUpdate(anonymousId))
			.thenReturn(Optional.of(state));

		assertThat(service.select(anonymousId, 1, "left")).isEqualTo(state);
		assertThatThrownBy(() -> service.select(anonymousId, 1, "right"))
			.isInstanceOf(WorldcupVoteRejectedException.class)
			.hasMessageContaining("바꿀 수 없습니다");
	}

	private GeneratedMatchGame gameWithOneMatchup() {
		MatchCard left = new MatchCard("left", "왼쪽", "서울", "권역", new BigDecimal("2.0"),
			100, 2000, null);
		MatchCard right = new MatchCard("right", "오른쪽", "서울", "권역", new BigDecimal("2.0"),
			100, 2000, null);
		return new GeneratedMatchGame("zone", new BigDecimal("0.2"),
			List.of(new Matchup(MatchType.WITHIN_ZONE, left, right)));
	}
}
