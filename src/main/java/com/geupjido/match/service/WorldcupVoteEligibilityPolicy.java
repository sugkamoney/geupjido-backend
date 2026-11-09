package com.geupjido.match.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.geupjido.match.model.WorldcupVoteForAggregation;

/** 확정된 선택 중 점수에 반영할 표를 고르고 권역별 전환 조건을 계산한다. */
@Component
public class WorldcupVoteEligibilityPolicy {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final int MIN_VOTES = 50;
	private static final int MIN_VISITORS = 10;
	private static final int MIN_CROSS_ZONE_VOTES = 20;

	public Result evaluate(List<WorldcupVoteForAggregation> recordedVotes) {
		List<WorldcupVoteForAggregation> chronological = recordedVotes.stream()
			.sorted(Comparator.comparing(WorldcupVoteForAggregation::selectedAt)
				.thenComparingLong(WorldcupVoteForAggregation::gameId)
				.thenComparingInt(WorldcupVoteForAggregation::roundNumber))
			.toList();
		List<WorldcupVoteForAggregation> valid = new ArrayList<>();
		Accumulator accumulator = newAccumulator(valid::add);
		chronological.forEach(accumulator);
		return new Result(valid, accumulator.countsByZone());
	}

	public Accumulator newAccumulator(Consumer<WorldcupVoteForAggregation> onValidVote) {
		return new Accumulator(onValidVote);
	}

	public record Result(List<WorldcupVoteForAggregation> validVotes,
		Map<String, ZoneCount> countsByZone) {
		public Result {
			validVotes = List.copyOf(validVotes);
			countsByZone = Map.copyOf(countsByZone);
		}
	}

	public record ZoneCount(int validVotes, int distinctVisitors, int crossZoneVotes) {
		public boolean readyForVoteRanking() {
			return validVotes >= MIN_VOTES && distinctVisitors >= MIN_VISITORS
				&& crossZoneVotes >= MIN_CROSS_ZONE_VOTES;
		}
	}

	public static final class Accumulator implements Consumer<WorldcupVoteForAggregation> {
		private final Consumer<WorldcupVoteForAggregation> onValidVote;
		private final Set<DailyPair> seenToday = new HashSet<>();
		private final Map<String, MutableZoneCount> byZone = new HashMap<>();
		private LocalDate currentDate;

		private Accumulator(Consumer<WorldcupVoteForAggregation> onValidVote) {
			this.onValidVote = onValidVote;
		}

		@Override
		public void accept(WorldcupVoteForAggregation vote) {
			LocalDate date = vote.selectedAt().atZone(SEOUL).toLocalDate();
			if (currentDate != null && date.isBefore(currentDate)) {
				throw new IllegalArgumentException("투표는 선택 시각순으로 전달해야 합니다.");
			}
			if (!date.equals(currentDate)) {
				seenToday.clear();
				currentDate = date;
			}
			if (!seenToday.add(DailyPair.from(vote))) {
				return;
			}
			onValidVote.accept(vote);
			byZone.computeIfAbsent(vote.leftZoneId(), ignored -> new MutableZoneCount())
				.add(vote.anonymousId(), vote.crossZone());
			if (vote.crossZone()) {
				byZone.computeIfAbsent(vote.rightZoneId(), ignored -> new MutableZoneCount())
					.add(vote.anonymousId(), true);
			}
		}

		public Map<String, ZoneCount> countsByZone() {
			Map<String, ZoneCount> counts = new HashMap<>();
			byZone.forEach((zoneId, count) -> counts.put(zoneId, count.snapshot()));
			return Map.copyOf(counts);
		}
	}

	private record DailyPair(UUID anonymousId, String firstComplexId, String secondComplexId) {
		private static DailyPair from(WorldcupVoteForAggregation vote) {
			String left = vote.leftComplexId();
			String right = vote.rightComplexId();
			return new DailyPair(vote.anonymousId(),
				left.compareTo(right) < 0 ? left : right,
				left.compareTo(right) < 0 ? right : left);
		}
	}

	private static class MutableZoneCount {
		private int validVotes;
		private int crossZoneVotes;
		private final Set<UUID> visitors = new HashSet<>();

		private void add(UUID anonymousId, boolean crossZone) {
			validVotes++;
			if (crossZone) {
				crossZoneVotes++;
			}
			visitors.add(anonymousId);
		}

		private ZoneCount snapshot() {
			return new ZoneCount(validVotes, visitors.size(), crossZoneVotes);
		}
	}
}
