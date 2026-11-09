package com.geupjido.match.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.geupjido.match.model.WorldcupVoteForAggregation;

/** 초기 가격·급지를 출발점으로 삼아 유효표의 상대별 승패를 점수로 환산한다. */
public final class WorldcupVoteRatingCalculator implements Consumer<WorldcupVoteForAggregation> {

	private static final int COMPLEX_PRIOR_VOTES = 10;
	private static final int ZONE_PRIOR_CROSS_VOTES = 20;
	private static final BigDecimal MIN_TIER = new BigDecimal("1.0");
	private static final BigDecimal MAX_TIER = new BigDecimal("5.9");

	private final Map<String, Rating> complexRatings = new HashMap<>();
	private final Map<String, Rating> zoneRatings = new HashMap<>();
	private final Map<String, String> currentZoneByComplex = new HashMap<>();
	private final Map<String, Double> initialComplexRatings = new HashMap<>();

	public WorldcupVoteRatingCalculator(List<ZoneSeed> zones, List<ComplexSeed> complexes) {
		Objects.requireNonNull(zones, "권역 초기값이 필요합니다.");
		Objects.requireNonNull(complexes, "단지 초기값이 필요합니다.");
		for (ZoneSeed zone : zones) {
			if (zoneRatings.putIfAbsent(zone.id(), new Rating(-zone.tier().doubleValue())) != null) {
				throw new IllegalArgumentException("중복된 권역 ID: " + zone.id());
			}
		}

		Map<String, List<Double>> pricesByZone = new HashMap<>();
		for (ComplexSeed complex : complexes) {
			if (!zoneRatings.containsKey(complex.zoneId())) {
				throw new IllegalArgumentException("초기 급지가 없는 권역: " + complex.zoneId());
			}
			if (currentZoneByComplex.putIfAbsent(complex.id(), complex.zoneId()) != null) {
				throw new IllegalArgumentException("중복된 단지 ID: " + complex.id());
			}
			if (complex.hasCurrentPrice()) {
				pricesByZone.computeIfAbsent(complex.zoneId(), ignored -> new ArrayList<>())
					.add(complex.unitPrice());
			}
		}

		Map<String, Double> medianByZone = new HashMap<>();
		pricesByZone.forEach((zoneId, prices) -> medianByZone.put(zoneId, median(prices)));
		for (ComplexSeed complex : complexes) {
			double initial = complex.initialRating() != null
				? complex.initialRating()
				: zoneRatings.get(complex.zoneId()).value
					+ Math.log(complex.unitPrice() / medianByZone.get(complex.zoneId()));
			initialComplexRatings.put(complex.id(), initial);
			complexRatings.put(complex.id(), new Rating(initial));
		}
	}

	public Map<String, Double> initialComplexRatings() {
		return Map.copyOf(initialComplexRatings);
	}

	@Override
	public void accept(WorldcupVoteForAggregation vote) {
		Rating leftComplex = requireRating(complexRatings, vote.leftComplexId(), "단지");
		Rating rightComplex = requireRating(complexRatings, vote.rightComplexId(), "단지");
		boolean leftWon = vote.selectedComplexId().equals(vote.leftComplexId());
		update(leftComplex, rightComplex, leftWon, COMPLEX_PRIOR_VOTES);

		if (vote.crossZone()) {
			Rating leftZone = requireRating(zoneRatings, vote.leftZoneId(), "권역");
			Rating rightZone = requireRating(zoneRatings, vote.rightZoneId(), "권역");
			update(leftZone, rightZone, leftWon, ZONE_PRIOR_CROSS_VOTES);
		}
	}

	public Snapshot snapshot() {
		Map<String, BigDecimal> complexScores = new HashMap<>();
		complexRatings.forEach((id, rating) -> complexScores.put(id, score(rating.value)));

		Map<String, ZoneScore> zoneScores = new HashMap<>();
		zoneRatings.forEach((id, rating) -> zoneScores.put(id,
			new ZoneScore(score(rating.value), displayTier(rating.value))));

		Map<String, List<String>> complexesByZone = new HashMap<>();
		currentZoneByComplex.forEach((complexId, zoneId) ->
			complexesByZone.computeIfAbsent(zoneId, ignored -> new ArrayList<>()).add(complexId));
		Map<String, Integer> ranks = new HashMap<>();
		for (List<String> complexIds : complexesByZone.values()) {
			complexIds.sort(Comparator.comparing((String id) -> complexScores.get(id))
				.reversed().thenComparing(id -> id));
			BigDecimal previousScore = null;
			int rank = 0;
			for (int index = 0; index < complexIds.size(); index++) {
				String id = complexIds.get(index);
				BigDecimal currentScore = complexScores.get(id);
				if (previousScore == null || currentScore.compareTo(previousScore) != 0) {
					rank = index + 1;
				}
				ranks.put(id, rank);
				previousScore = currentScore;
			}
		}
		return new Snapshot(complexScores, ranks, zoneScores);
	}

	private static void update(Rating left, Rating right, boolean leftWon, int priorVotes) {
		double expectedLeft = 1.0 / (1.0 + Math.exp(right.value - left.value));
		double difference = (leftWon ? 1.0 : 0.0) - expectedLeft;
		left.value += 4.0 * difference / (priorVotes + left.votes + 1);
		right.value -= 4.0 * difference / (priorVotes + right.votes + 1);
		left.votes++;
		right.votes++;
	}

	private static Rating requireRating(Map<String, Rating> ratings, String id, String label) {
		Rating rating = ratings.get(id);
		if (rating == null) {
			throw new IllegalArgumentException("초기값이 없는 " + label + ": " + id);
		}
		return rating;
	}

	private static double median(List<Double> values) {
		values.sort(Double::compareTo);
		int middle = values.size() / 2;
		return values.size() % 2 == 1
			? values.get(middle)
			: (values.get(middle - 1) + values.get(middle)) / 2.0;
	}

	private static BigDecimal score(double rating) {
		return BigDecimal.valueOf(100.0 + 10.0 * rating)
			.setScale(2, RoundingMode.HALF_UP);
	}

	private static BigDecimal displayTier(double rating) {
		BigDecimal tier = BigDecimal.valueOf(-rating).setScale(1, RoundingMode.HALF_UP);
		return tier.max(MIN_TIER).min(MAX_TIER);
	}

	public record ZoneSeed(String id, BigDecimal tier) {
		public ZoneSeed {
			if (id == null || id.isBlank() || tier == null
				|| tier.compareTo(MIN_TIER) < 0 || tier.compareTo(MAX_TIER) > 0) {
				throw new IllegalArgumentException("권역 ID와 1.0~5.9 사이의 초기 급지가 필요합니다.");
			}
		}
	}

	public record ComplexSeed(
		String id, String zoneId, Integer price, BigDecimal area, Double initialRating
	) {
		public ComplexSeed(String id, String zoneId, int price, BigDecimal area) {
			this(id, zoneId, price, area, null);
		}

		public ComplexSeed {
			if (id == null || id.isBlank() || zoneId == null || zoneId.isBlank()
				|| (initialRating == null && (price == null || price <= 0
					|| area == null || area.signum() <= 0))
				|| (initialRating != null && !Double.isFinite(initialRating))) {
				throw new IllegalArgumentException("단지 ID·권역과 초기 평가값 또는 대표 가격·면적이 필요합니다.");
			}
		}

		private boolean hasCurrentPrice() {
			return price != null && price > 0 && area != null && area.signum() > 0;
		}

		private double unitPrice() {
			return price / area.doubleValue();
		}
	}

	public record ZoneScore(BigDecimal score, BigDecimal tier) {
	}

	public record Snapshot(
		Map<String, BigDecimal> complexScores,
		Map<String, Integer> zoneRanks,
		Map<String, ZoneScore> zoneScores
	) {
		public Snapshot {
			complexScores = Map.copyOf(complexScores);
			zoneRanks = Map.copyOf(zoneRanks);
			zoneScores = Map.copyOf(zoneScores);
		}
	}

	private static final class Rating {
		private double value;
		private int votes;

		private Rating(double value) {
			this.value = value;
		}
	}
}
