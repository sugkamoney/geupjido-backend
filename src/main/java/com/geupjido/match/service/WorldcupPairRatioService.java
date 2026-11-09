package com.geupjido.match.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.WorldcupGameState;
import com.geupjido.match.model.WorldcupPairRatio;
import com.geupjido.match.repository.WorldcupPairRatioRepository;

@Service
public class WorldcupPairRatioService {

	private final WorldcupPairRatioRepository repository;

	public WorldcupPairRatioService(WorldcupPairRatioRepository repository) {
		this.repository = repository;
	}

	public Optional<WorldcupPairRatio> forRound(WorldcupGameState state, int roundNumber) {
		Matchup matchup = state.game().matchups().get(roundNumber - 1);
		return repository.findVisibleRatio(
			matchup.left().complexId(), matchup.right().complexId());
	}
}
