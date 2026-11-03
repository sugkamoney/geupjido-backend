package com.geupjido.match.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.repository.WorldcupGameRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorldcupGameServiceTest {

	private final MatchGenerationService generator = mock(MatchGenerationService.class);
	private final WorldcupGameRepository repository = mock(WorldcupGameRepository.class);
	private final WorldcupGameService service = new WorldcupGameService(generator, repository);

	@Test
	void 생성된_게임을_익명_식별자와_함께_저장한다() {
		UUID anonymousId = UUID.randomUUID();
		GeneratedMatchGame game = new GeneratedMatchGame("zone", new BigDecimal("0.2"), List.of());
		when(generator.generateForZone("zone")).thenReturn(game);

		assertThat(service.startForZone(anonymousId, "zone")).isEqualTo(game);

		verify(repository).save(anonymousId, game);
	}

	@Test
	void 대진을_만들지_못하면_게임을_저장하지_않는다() {
		UUID anonymousId = UUID.randomUUID();
		when(generator.generateFromBanner())
			.thenThrow(new MatchUnavailableException("대진 부족"));

		assertThatThrownBy(() -> service.startFromBanner(anonymousId))
			.isInstanceOf(MatchUnavailableException.class);
		verifyNoInteractions(repository);
	}

	@Test
	void 현재_게임은_요청한_익명_식별자로만_조회한다() {
		UUID anonymousId = UUID.randomUUID();
		GeneratedMatchGame game = new GeneratedMatchGame("zone", new BigDecimal("0.2"), List.of());
		when(repository.findLatestByAnonymousId(anonymousId)).thenReturn(Optional.of(game));

		assertThat(service.findCurrent(anonymousId)).isEqualTo(game);
		assertThatThrownBy(() -> service.findCurrent(UUID.randomUUID()))
			.isInstanceOf(WorldcupGameNotFoundException.class);
	}
}
