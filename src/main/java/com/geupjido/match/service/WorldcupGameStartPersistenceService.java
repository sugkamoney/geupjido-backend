package com.geupjido.match.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.repository.WorldcupGameRepository;

@Service
public class WorldcupGameStartPersistenceService {

	private final WorldcupGameRepository gameRepository;

	public WorldcupGameStartPersistenceService(WorldcupGameRepository gameRepository) {
		this.gameRepository = gameRepository;
	}

	@Transactional
	public WorldcupGameStartResult saveOrResume(UUID anonymousId, GeneratedMatchGame game) {
		gameRepository.lockAnonymousId(anonymousId);
		var latest = gameRepository.findLatestByAnonymousIdForUpdate(anonymousId);
		if (latest.isPresent() && !latest.get().completed()) {
			return new WorldcupGameStartResult(latest.get(), false);
		}
		return new WorldcupGameStartResult(gameRepository.save(anonymousId, game), true);
	}
}
