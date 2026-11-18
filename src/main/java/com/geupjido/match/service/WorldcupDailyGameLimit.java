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
		Day day = dayAt(startedAt);
		if (gameRepository.countStartedByAnonymousId(anonymousId, day.fromInclusive(), day.toExclusive())
			>= MAX_GAMES_PER_DAY) {
			throw new WorldcupDailyLimitReachedException();
		}
		return startedAt;
	}

	public Day currentDay() {
		return dayAt(clock.instant());
	}

	public Instant now() {
		return clock.instant();
	}

	public Day dayAt(Instant instant) {
		LocalDate date = LocalDate.ofInstant(instant, KOREA_TIME);
		return new Day(date, date.atStartOfDay(KOREA_TIME).toInstant(),
			date.plusDays(1).atStartOfDay(KOREA_TIME).toInstant());
	}

	public record Day(LocalDate date, Instant fromInclusive, Instant toExclusive) {
	}
}
