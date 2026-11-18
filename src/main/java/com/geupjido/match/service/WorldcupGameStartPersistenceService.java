package com.geupjido.match.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.WorldcupGameMode;
import com.geupjido.match.repository.WorldcupGameRepository;

@Service
public class WorldcupGameStartPersistenceService {

	private final WorldcupGameRepository gameRepository;
	private final WorldcupDailyGameLimit dailyGameLimit;

	public WorldcupGameStartPersistenceService(
		WorldcupGameRepository gameRepository,
		WorldcupDailyGameLimit dailyGameLimit
	) {
		this.gameRepository = gameRepository;
		this.dailyGameLimit = dailyGameLimit;
	}

	@Transactional
	public WorldcupGameStartResult saveOrResume(UUID anonymousId, GeneratedMatchGame game) {
		return saveOrResume(anonymousId, game, WorldcupGameMode.GENERAL);
	}

	@Transactional
	public WorldcupGameStartResult saveOrResume(UUID anonymousId, GeneratedMatchGame game,
		WorldcupGameMode mode) {
		gameRepository.lockAnonymousId(anonymousId);
		WorldcupDailyGameLimit.Day day = mode == WorldcupGameMode.GENERAL
			? dailyGameLimit.currentDay() : null;
		var latest = gameRepository.findLatest(anonymousId, mode,
			mode == WorldcupGameMode.ZONE_COMPLEX ? game.baseZoneId() : null,
			day == null ? null : day.fromInclusive(),
			day == null ? null : day.toExclusive(), true);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		Instant startedAt = mode == WorldcupGameMode.GENERAL
			? dailyGameLimit.ensureCanStart(anonymousId) : dailyGameLimit.now();
		return new WorldcupGameStartResult(gameRepository.save(anonymousId, game, startedAt, mode), true);
	}
}
