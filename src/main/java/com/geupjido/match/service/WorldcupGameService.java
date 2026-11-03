package com.geupjido.match.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.repository.WorldcupGameRepository;

@Service
public class WorldcupGameService {

	private final MatchGenerationService matchGenerationService;
	private final WorldcupGameRepository gameRepository;

	public WorldcupGameService(
		MatchGenerationService matchGenerationService,
		WorldcupGameRepository gameRepository
	) {
		this.matchGenerationService = matchGenerationService;
		this.gameRepository = gameRepository;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public GeneratedMatchGame startFromBanner(UUID anonymousId) {
		GeneratedMatchGame game = matchGenerationService.generateFromBanner();
		gameRepository.save(anonymousId, game);
		return game;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public GeneratedMatchGame startForZone(UUID anonymousId, String zoneId) {
		GeneratedMatchGame game = matchGenerationService.generateForZone(zoneId);
		gameRepository.save(anonymousId, game);
		return game;
	}

	@Transactional(readOnly = true)
	public GeneratedMatchGame findCurrent(UUID anonymousId) {
		return gameRepository.findLatestByAnonymousId(anonymousId)
			.orElseThrow(WorldcupGameNotFoundException::new);
	}
}
