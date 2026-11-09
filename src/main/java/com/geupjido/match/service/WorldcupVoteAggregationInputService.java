package com.geupjido.match.service;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.model.WorldcupVoteForAggregation;
import com.geupjido.match.repository.WorldcupVoteAggregationRepository;

@Service
public class WorldcupVoteAggregationInputService {

	private final WorldcupVoteAggregationRepository repository;
	private final WorldcupVoteEligibilityPolicy eligibilityPolicy;

	public WorldcupVoteAggregationInputService(
		WorldcupVoteAggregationRepository repository,
		WorldcupVoteEligibilityPolicy eligibilityPolicy
	) {
		this.repository = repository;
		this.eligibilityPolicy = eligibilityPolicy;
	}

	@Transactional(readOnly = true)
	public Map<String, WorldcupVoteEligibilityPolicy.ZoneCount> forEachValidVote(
		Consumer<WorldcupVoteForAggregation> consumer
	) {
		var accumulator = eligibilityPolicy.newAccumulator(consumer);
		repository.forEachRecordedVote(accumulator);
		return accumulator.countsByZone();
	}

	@Transactional(readOnly = true)
	public OutcomeInput collectOutcomes() {
		var tally = new WorldcupVoteOutcomeTally();
		var zoneCounts = forEachValidVote(tally);
		return new OutcomeInput(zoneCounts, tally.snapshot());
	}

	public record OutcomeInput(
		Map<String, WorldcupVoteEligibilityPolicy.ZoneCount> zoneCounts,
		WorldcupVoteOutcomeTally.Snapshot outcomes
	) {
		public OutcomeInput {
			zoneCounts = Map.copyOf(zoneCounts);
		}
	}

	@Transactional(readOnly = true)
	public RatingInput calculateRatings(
		List<WorldcupVoteRatingCalculator.ZoneSeed> zones,
		List<WorldcupVoteRatingCalculator.ComplexSeed> complexes
	) {
		var calculator = new WorldcupVoteRatingCalculator(zones, complexes);
		var zoneCounts = forEachValidVote(calculator);
		return new RatingInput(zoneCounts, calculator.snapshot(), calculator.initialComplexRatings());
	}

	public record RatingInput(
		Map<String, WorldcupVoteEligibilityPolicy.ZoneCount> zoneCounts,
		WorldcupVoteRatingCalculator.Snapshot ratings,
		Map<String, Double> initialComplexRatings
	) {
		public RatingInput {
			zoneCounts = Map.copyOf(zoneCounts);
			initialComplexRatings = Map.copyOf(initialComplexRatings);
		}
	}
}
