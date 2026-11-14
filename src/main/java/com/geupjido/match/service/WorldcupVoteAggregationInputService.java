package com.geupjido.match.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
		// forEachValidVote가 중복 선택을 제외한 표만 아래 람다로 전달한다.
		// 권역별 집계와 별개로 지도 상태 카드에 쓸 전체 고유 방문자와 표 수를 센다.
		Set<UUID> participants = new HashSet<>();
		AtomicLong validVotes = new AtomicLong();
		var zoneCounts = forEachValidVote(vote -> {
			// 같은 '유효표'를 평가 계산과 상태 카드 숫자에 모두 사용해야
			// 화면의 표 수와 실제 급지 전환 조건이 어긋나지 않는다.
			calculator.accept(vote);
			participants.add(vote.anonymousId());
			validVotes.incrementAndGet();
		});
		return new RatingInput(zoneCounts, calculator.snapshot(),
			calculator.initialComplexRatings(), validVotes.get(), participants.size());
	}

	public record RatingInput(
		Map<String, WorldcupVoteEligibilityPolicy.ZoneCount> zoneCounts,
		WorldcupVoteRatingCalculator.Snapshot ratings,
		Map<String, Double> initialComplexRatings,
		long validVoteCount,
		long participantCount
	) {
		public RatingInput {
			zoneCounts = Map.copyOf(zoneCounts);
			initialComplexRatings = Map.copyOf(initialComplexRatings);
		}
	}
}
