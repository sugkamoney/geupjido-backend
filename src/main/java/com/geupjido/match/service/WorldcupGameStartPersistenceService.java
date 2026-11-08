package com.geupjido.match.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
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
		gameRepository.lockAnonymousId(anonymousId);
		var latest = gameRepository.findLatestByAnonymousIdForUpdate(anonymousId);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		Instant startedAt = dailyGameLimit.ensureCanStart(anonymousId);
		return new WorldcupGameStartResult(gameRepository.save(anonymousId, game, startedAt), true);
	}
}
