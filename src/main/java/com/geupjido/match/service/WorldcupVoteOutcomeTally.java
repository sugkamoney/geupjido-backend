package com.geupjido.match.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.geupjido.match.model.WorldcupVoteForAggregation;

/** 유효표의 승패를 단지별로 세고, 권역 간 대결은 권역별로도 센다. */
public final class WorldcupVoteOutcomeTally implements Consumer<WorldcupVoteForAggregation> {

	private final Map<String, MutableOutcome> byComplex = new HashMap<>();
	private final Map<String, MutableOutcome> crossZoneByZone = new HashMap<>();

	@Override
	public void accept(WorldcupVoteForAggregation vote) {
		boolean leftWon = vote.selectedComplexId().equals(vote.leftComplexId());
		byComplex.computeIfAbsent(vote.leftComplexId(), ignored -> new MutableOutcome())
			.add(leftWon);
		byComplex.computeIfAbsent(vote.rightComplexId(), ignored -> new MutableOutcome())
			.add(!leftWon);

		if (vote.crossZone()) {
			crossZoneByZone.computeIfAbsent(vote.leftZoneId(), ignored -> new MutableOutcome())
				.add(leftWon);
			crossZoneByZone.computeIfAbsent(vote.rightZoneId(), ignored -> new MutableOutcome())
				.add(!leftWon);
		}
	}

	public Snapshot snapshot() {
		return new Snapshot(copy(byComplex), copy(crossZoneByZone));
	}

	private static Map<String, Outcome> copy(Map<String, MutableOutcome> source) {
		Map<String, Outcome> result = new HashMap<>();
		source.forEach((id, count) -> result.put(id, new Outcome(count.wins, count.losses)));
		return Map.copyOf(result);
	}

	public record Outcome(int wins, int losses) {
		public int total() {
			return wins + losses;
		}
	}

	public record Snapshot(
		Map<String, Outcome> byComplex,
		Map<String, Outcome> crossZoneByZone
	) {
		public Snapshot {
			byComplex = Map.copyOf(byComplex);
			crossZoneByZone = Map.copyOf(crossZoneByZone);
		}
	}

	private static final class MutableOutcome {
		private int wins;
		private int losses;

		private void add(boolean won) {
			if (won) {
				wins++;
			} else {
				losses++;
			}
		}
	}
}
