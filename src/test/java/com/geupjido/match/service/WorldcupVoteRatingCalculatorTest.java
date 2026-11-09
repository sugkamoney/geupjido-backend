package com.geupjido.match.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.WorldcupVoteForAggregation;

import static org.assertj.core.api.Assertions.assertThat;

class WorldcupVoteRatingCalculatorTest {

	@Test
	void 투표가_없으면_초기_급지와_제곱미터당_가격으로_순위를_정한다() {
		var calculator = new WorldcupVoteRatingCalculator(
			List.of(zone("z", "2.0")),
			List.of(complex("A", "z", 20000), complex("B", "z", 20000),
				complex("C", "z", 10000)));

		var result = calculator.snapshot();

		assertThat(result.zoneScores().get("z").tier()).isEqualByComparingTo("2.0");
		assertThat(result.zoneRanks()).containsEntry("A", 1).containsEntry("B", 1)
			.containsEntry("C", 3);
		assertThat(result.complexScores().get("A"))
			.isGreaterThan(result.complexScores().get("C"));
	}

	@Test
	void 강한_상대를_이긴_표는_약한_상대를_이긴_표보다_점수를_더_올린다() {
		var zones = List.of(zone("base", "2.0"), zone("strong", "1.0"),
			zone("weak", "3.0"));
		var complexes = List.of(complex("A", "base", 10000),
			complex("B", "strong", 10000), complex("C", "weak", 10000));
		var strongOpponent = new WorldcupVoteRatingCalculator(zones, complexes);
		var weakOpponent = new WorldcupVoteRatingCalculator(zones, complexes);

		strongOpponent.accept(vote("A", "B", "A", "base", "strong"));
		weakOpponent.accept(vote("A", "C", "A", "base", "weak"));

		assertThat(strongOpponent.snapshot().complexScores().get("A"))
			.isGreaterThan(weakOpponent.snapshot().complexScores().get("A"));
		assertThat(strongOpponent.snapshot().zoneScores().get("base").score())
			.isGreaterThan(weakOpponent.snapshot().zoneScores().get("base").score());
	}

	@Test
	void 같은_권역_대결은_단지_점수만_변경하고_다른_권역_대결은_양쪽_급지도_변경한다() {
		var calculator = new WorldcupVoteRatingCalculator(
			List.of(zone("z", "2.0"), zone("other", "2.0")),
			List.of(complex("A", "z", 10000), complex("B", "z", 10000),
				complex("C", "other", 10000)));

		calculator.accept(vote("A", "B", "A", "z", "z"));
		var afterWithin = calculator.snapshot();
		assertThat(afterWithin.zoneScores().get("z").tier()).isEqualByComparingTo("2.0");
		assertThat(afterWithin.complexScores().get("A"))
			.isGreaterThan(afterWithin.complexScores().get("B"));

		calculator.accept(vote("A", "C", "A", "z", "other"));
		var afterCross = calculator.snapshot();
		assertThat(afterCross.zoneScores().get("z").tier()).isLessThan(new BigDecimal("2.0"));
		assertThat(afterCross.zoneScores().get("other").tier())
			.isGreaterThan(new BigDecimal("2.0"));
	}

	@Test
	void 첫_동급_대결에서_단지와_권역의_초기값은_각각_10표와_20표만큼_완충된다() {
		var calculator = new WorldcupVoteRatingCalculator(
			List.of(zone("z", "2.0"), zone("other", "2.0")),
			List.of(complex("A", "z", 10000), complex("B", "other", 10000)));

		calculator.accept(vote("A", "B", "A", "z", "other"));
		var result = calculator.snapshot();

		assertThat(result.complexScores().get("A")).isEqualByComparingTo("81.82");
		assertThat(result.zoneScores().get("z").score()).isEqualByComparingTo("80.95");
	}

	@Test
	void 초기_평가값이_고정되면_현재_가격이_변해도_누적_투표_점수는_같다() {
		var zone = List.of(zone("z", "2.0"));
		var first = new WorldcupVoteRatingCalculator(zone,
			List.of(complex("A", "z", 20000), complex("B", "z", 10000)));
		var frozen = first.initialComplexRatings();
		var second = new WorldcupVoteRatingCalculator(zone, List.of(
			new WorldcupVoteRatingCalculator.ComplexSeed(
				"A", "z", 50000, new BigDecimal("100"), frozen.get("A")),
			new WorldcupVoteRatingCalculator.ComplexSeed(
				"B", "z", null, null, frozen.get("B"))));
		var vote = vote("A", "B", "B", "z", "z");

		first.accept(vote);
		second.accept(vote);

		assertThat(second.snapshot()).isEqualTo(first.snapshot());
	}

	private static WorldcupVoteRatingCalculator.ZoneSeed zone(String id, String tier) {
		return new WorldcupVoteRatingCalculator.ZoneSeed(id, new BigDecimal(tier));
	}

	private static WorldcupVoteRatingCalculator.ComplexSeed complex(
		String id, String zoneId, int price
	) {
		return new WorldcupVoteRatingCalculator.ComplexSeed(id, zoneId,
			price, new BigDecimal("100.0"));
	}

	private static WorldcupVoteForAggregation vote(
		String left, String right, String selected, String leftZone, String rightZone
	) {
		return new WorldcupVoteForAggregation(1, 1, new UUID(0, 1),
			left, right, selected, leftZone, rightZone,
			Instant.parse("2026-11-08T00:00:00Z"));
	}
}
