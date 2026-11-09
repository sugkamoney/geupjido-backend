package com.geupjido.match.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.WorldcupVoteForAggregation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldcupVoteEligibilityPolicyTest {

	private final WorldcupVoteEligibilityPolicy policy = new WorldcupVoteEligibilityPolicy();

	@Test
	void 같은_방문자의_같은_단지쌍은_좌우가_바뀌어도_한국_날짜별_첫_선택만_집계한다() {
		UUID visitor = new UUID(0, 1);
		WorldcupVoteForAggregation first = vote(1, visitor, "A1", "B1", "A1",
			"zone-a", "zone-b", "2026-11-08T14:59:00Z");
		WorldcupVoteForAggregation repeated = vote(2, visitor, "B1", "A1", "B1",
			"zone-b", "zone-a", "2026-11-08T14:59:30Z");
		WorldcupVoteForAggregation nextDay = vote(3, visitor, "B1", "A1", "B1",
			"zone-b", "zone-a", "2026-11-08T15:00:00Z");

		var result = policy.evaluate(List.of(repeated, nextDay, first));

		assertThat(result.validVotes()).containsExactly(first, nextDay);
		assertThat(result.countsByZone().get("zone-a"))
			.isEqualTo(new WorldcupVoteEligibilityPolicy.ZoneCount(2, 1, 2));
		assertThat(result.countsByZone().get("zone-b"))
			.isEqualTo(new WorldcupVoteEligibilityPolicy.ZoneCount(2, 1, 2));
	}

	@Test
	void 권역_안의_한_표는_한_권역에만_세고_권역_간_표는_양쪽에_센다() {
		UUID visitor = new UUID(0, 1);
		var within = vote(1, visitor, "A1", "A2", "A1",
			"zone-a", "zone-a", "2026-11-08T00:00:00Z");
		var cross = vote(2, visitor, "A1", "B1", "A1",
			"zone-a", "zone-b", "2026-11-08T00:00:01Z");

		var result = policy.evaluate(List.of(within, cross));

		assertThat(result.countsByZone().get("zone-a"))
			.isEqualTo(new WorldcupVoteEligibilityPolicy.ZoneCount(2, 1, 1));
		assertThat(result.countsByZone().get("zone-b"))
			.isEqualTo(new WorldcupVoteEligibilityPolicy.ZoneCount(1, 1, 1));
	}

	@Test
	void 권역은_유효표_50표_방문자_10명_권역간_20표를_모두_채워야_전환한다() {
		assertThat(policy.evaluate(votes(50, 20, 10)).countsByZone()
			.get("zone-a").readyForVoteRanking()).isTrue();
		assertThat(policy.evaluate(votes(49, 20, 10)).countsByZone()
			.get("zone-a").readyForVoteRanking()).isFalse();
		assertThat(policy.evaluate(votes(50, 19, 10)).countsByZone()
			.get("zone-a").readyForVoteRanking()).isFalse();
		assertThat(policy.evaluate(votes(50, 20, 9)).countsByZone()
			.get("zone-a").readyForVoteRanking()).isFalse();
	}

	@Test
	void 스트리밍_집계는_하루가_바뀌면_같은_단지쌍을_다시_인정하고_시간_역행은_거부한다() {
		UUID visitor = new UUID(0, 1);
		var first = vote(1, visitor, "A1", "B1", "A1",
			"zone-a", "zone-b", "2026-11-08T14:59:00Z");
		var repeated = vote(2, visitor, "B1", "A1", "B1",
			"zone-b", "zone-a", "2026-11-08T14:59:30Z");
		var nextDay = vote(3, visitor, "B1", "A1", "B1",
			"zone-b", "zone-a", "2026-11-08T15:00:00Z");
		List<WorldcupVoteForAggregation> valid = new ArrayList<>();
		var accumulator = policy.newAccumulator(valid::add);

		accumulator.accept(first);
		accumulator.accept(repeated);
		accumulator.accept(nextDay);

		assertThat(valid).containsExactly(first, nextDay);
		assertThat(accumulator.countsByZone().get("zone-a").validVotes()).isEqualTo(2);
		assertThatThrownBy(() -> accumulator.accept(first))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private static List<WorldcupVoteForAggregation> votes(
		int total, int crossZone, int visitors
	) {
		List<WorldcupVoteForAggregation> votes = new ArrayList<>();
		Instant start = Instant.parse("2026-11-08T00:00:00Z");
		for (int index = 0; index < total; index++) {
			boolean cross = index < crossZone;
			String rightZone = cross ? "zone-b" : "zone-a";
			votes.add(new WorldcupVoteForAggregation(index + 1, 1,
				new UUID(0, index % visitors + 1), "A" + index,
				(cross ? "B" : "C") + index, "A" + index,
				"zone-a", rightZone, start.plusSeconds(index)));
		}
		return votes;
	}

	private static WorldcupVoteForAggregation vote(
		long gameId, UUID visitor, String leftId, String rightId, String selectedId,
		String leftZone, String rightZone, String selectedAt
	) {
		return new WorldcupVoteForAggregation(gameId, 1, visitor, leftId, rightId,
			selectedId, leftZone, rightZone, Instant.parse(selectedAt));
	}
}
