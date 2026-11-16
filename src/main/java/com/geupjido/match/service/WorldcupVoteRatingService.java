package com.geupjido.match.service;

import java.time.Instant;
import java.util.Objects;

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

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public Calculation calculateBefore(Instant exclusiveCutoff) {
		Objects.requireNonNull(exclusiveCutoff, "집계 마감 시각이 필요합니다.");
		var seeds = seedRepository.loadSeeds();
		var input = aggregationInputService.calculateRatingsBefore(
			seeds.zones(), seeds.complexes(), exclusiveCutoff);
		return new Calculation(seeds, input);
	}

	public record Calculation(Seeds seeds, WorldcupVoteAggregationInputService.RatingInput input) {
	}
}
