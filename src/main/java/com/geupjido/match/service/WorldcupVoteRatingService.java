package com.geupjido.match.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.repository.WorldcupVoteRatingSeedRepository;
import com.geupjido.match.repository.WorldcupVoteRatingSeedRepository.Seeds;

@Service
public class WorldcupVoteRatingService {

	private final WorldcupVoteRatingSeedRepository seedRepository;
	private final WorldcupVoteAggregationInputService aggregationInputService;

	public WorldcupVoteRatingService(
		WorldcupVoteRatingSeedRepository seedRepository,
		WorldcupVoteAggregationInputService aggregationInputService
	) {
		this.seedRepository = seedRepository;
		this.aggregationInputService = aggregationInputService;
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Calculation calculate() {
		var seeds = seedRepository.loadSeeds();
		var input = aggregationInputService.calculateRatings(seeds.zones(), seeds.complexes());
		return new Calculation(seeds, input);
	}

	public record Calculation(Seeds seeds, WorldcupVoteAggregationInputService.RatingInput input) {
	}
}
