package com.geupjido.match.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.repository.WorldcupGameRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldcupDailyGameLimitTest {

	private final WorldcupGameRepository repository = mock(WorldcupGameRepository.class);
	private final UUID anonymousId = UUID.randomUUID();

	@Test
	void 한국_자정_직전에는_당일_범위만_센다() {
		WorldcupDailyGameLimit limit = new WorldcupDailyGameLimit(repository,
			Clock.fixed(Instant.parse("2026-11-04T14:59:59Z"), ZoneOffset.UTC));

		limit.ensureCanStart(anonymousId);

		verify(repository).countStartedByAnonymousId(anonymousId,
			Instant.parse("2026-11-03T15:00:00Z"),
			Instant.parse("2026-11-04T15:00:00Z"));
	}

	@Test
	void 한국_자정부터_새_날짜의_게임을_센다() {
		WorldcupDailyGameLimit limit = new WorldcupDailyGameLimit(repository,
			Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC));

		limit.ensureCanStart(anonymousId);

		verify(repository).countStartedByAnonymousId(anonymousId,
			Instant.parse("2026-11-04T15:00:00Z"),
			Instant.parse("2026-11-05T15:00:00Z"));
	}

	@Test
	void 여섯_게임을_시작했다면_다음_시작을_거부한다() {
		Instant from = Instant.parse("2026-11-04T15:00:00Z");
		Instant to = Instant.parse("2026-11-05T15:00:00Z");
		WorldcupDailyGameLimit limit = new WorldcupDailyGameLimit(repository,
			Clock.fixed(from, ZoneOffset.UTC));
		when(repository.countStartedByAnonymousId(anonymousId, from, to)).thenReturn(6L);

		assertThatThrownBy(() -> limit.ensureCanStart(anonymousId))
			.isInstanceOf(WorldcupDailyLimitReachedException.class);
	}
}
