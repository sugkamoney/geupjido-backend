package com.geupjido.match.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.WorldcupZoneRanking;
import com.geupjido.match.repository.WorldcupZoneRankingRepository;

@Service
public class WorldcupZoneRankingService {

	private final WorldcupZoneRankingRepository repository;

	public WorldcupZoneRankingService(WorldcupZoneRankingRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public WorldcupZoneRanking get(String zoneId) {
		return repository.findByZoneId(zoneId)
			.orElseThrow(WorldcupZoneRankingNotFoundException::new);
	}
}
