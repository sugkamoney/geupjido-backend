package com.geupjido.match.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.geupjido.match.repository.WorldcupGameRepository;

@Component
public class WorldcupDailyGameLimit {

	private static final int MAX_GAMES_PER_DAY = 6;
	private static final ZoneId KOREA_TIME = ZoneId.of("Asia/Seoul");

	private final WorldcupGameRepository gameRepository;
	private final Clock clock;

	public WorldcupDailyGameLimit(WorldcupGameRepository gameRepository, Clock clock) {
		this.gameRepository = gameRepository;
		this.clock = clock;
	}

	public Instant ensureCanStart(UUID anonymousId) {
		Instant startedAt = clock.instant();
		LocalDate today = LocalDate.ofInstant(startedAt, KOREA_TIME);
		Instant fromInclusive = today.atStartOfDay(KOREA_TIME).toInstant();
		Instant toExclusive = today.plusDays(1).atStartOfDay(KOREA_TIME).toInstant();
		if (gameRepository.countStartedByAnonymousId(anonymousId, fromInclusive, toExclusive)
			>= MAX_GAMES_PER_DAY) {
			throw new WorldcupDailyLimitReachedException();
		}
		return startedAt;
	}
}
