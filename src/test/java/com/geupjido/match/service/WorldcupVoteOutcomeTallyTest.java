package com.geupjido.match.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.WorldcupVoteForAggregation;

import static org.assertj.core.api.Assertions.assertThat;

class WorldcupVoteOutcomeTallyTest {

	@Test
	void 유효표만_단지_승패에_반영하고_권역간_표만_권역_승패에_반영한다() {
		UUID visitor = new UUID(0, 1);
		var cross = vote(1, visitor, "A", "B", "A", "zone-a", "zone-b", 0);
		var duplicate = vote(2, visitor, "B", "A", "B", "zone-b", "zone-a", 1);
		var within = vote(3, visitor, "A", "C", "C", "zone-a", "zone-a", 2);
		var tally = new WorldcupVoteOutcomeTally();

		var counts = new WorldcupVoteEligibilityPolicy()
			.newAccumulator(tally);
		List.of(cross, duplicate, within).forEach(counts);
		var result = tally.snapshot();

		assertThat(result.byComplex()).containsEntry("A", new WorldcupVoteOutcomeTally.Outcome(1, 1))
			.containsEntry("B", new WorldcupVoteOutcomeTally.Outcome(0, 1))
			.containsEntry("C", new WorldcupVoteOutcomeTally.Outcome(1, 0));
		assertThat(result.crossZoneByZone())
			.containsEntry("zone-a", new WorldcupVoteOutcomeTally.Outcome(1, 0))
			.containsEntry("zone-b", new WorldcupVoteOutcomeTally.Outcome(0, 1));
		assertThat(result.byComplex().get("A").total()).isEqualTo(2);
		assertThat(counts.countsByZone().get("zone-a").validVotes()).isEqualTo(2);
	}

	private static WorldcupVoteForAggregation vote(
		long gameId, UUID visitor, String left, String right, String selected,
		String leftZone, String rightZone, int seconds
	) {
		return new WorldcupVoteForAggregation(gameId, 1, visitor, left, right,
			selected, leftZone, rightZone,
			Instant.parse("2026-11-08T00:00:00Z").plusSeconds(seconds));
	}
}
