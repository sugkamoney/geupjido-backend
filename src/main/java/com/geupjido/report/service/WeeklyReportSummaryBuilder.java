package com.geupjido.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.zone.domain.RankingBasis;

/** 저장된 마감 스냅샷의 사실만 사용해 공개 리포트의 요약 문장을 만든다. */
@Component
public class WeeklyReportSummaryBuilder {
	private static final Comparator<Change> CHANGE_ORDER = Comparator
		.comparing(Change::delta, Comparator.reverseOrder())
		.thenComparing(change -> change.current().zoneName())
		.thenComparing(change -> change.current().zoneId());

	public String build(Instant periodEndAt, boolean hasPreviousReport,
		List<Zone> previous, List<Zone> current) {
		int variant = Math.floorMod(periodEndAt.getEpochSecond() / 604_800, 3);
		List<String> sentences = new ArrayList<>();
		if (hasPreviousReport) {
			appendWeeklyComparison(sentences, previous, current, variant);
		} else {
			sentences.add(switch (variant) {
				case 0 -> "첫 민심 급지 리포트예요.";
				case 1 -> "민심 급지를 처음으로 리포트에 담았어요.";
				default -> "이번 주에 첫 민심 급지 리포트를 발행해요.";
			});
		}
		appendInitialComparison(sentences, current, variant);
		return String.join(" ", sentences);
	}

	private void appendWeeklyComparison(List<String> sentences, List<Zone> previous,
		List<Zone> current, int variant) {
		Map<String, Zone> previousById = new HashMap<>();
		previous.forEach(zone -> previousById.put(zone.zoneId(), zone));
		List<Change> rises = new ArrayList<>();
		List<Change> falls = new ArrayList<>();
		for (Zone zone : current) {
			Zone old = previousById.get(zone.zoneId());
			if (zone.rankingBasis() != RankingBasis.VOTE || old == null
				|| old.rankingBasis() != RankingBasis.VOTE) {
				continue;
			}
			int direction = zone.voteTier().compareTo(old.voteTier());
			if (direction != 0) {
				Change change = new Change(zone, old.voteTier(),
					zone.voteTier().subtract(old.voteTier()).abs());
				(direction < 0 ? rises : falls).add(change);
			}
		}
		if (rises.isEmpty() && falls.isEmpty()) {
			sentences.add(switch (variant) {
				case 0 -> "지난 리포트와 비교해 민심 급지가 변한 권역은 없어요.";
				case 1 -> "이번 주에는 지난 리포트와 달라진 민심 급지가 없어요.";
				default -> "지난주와 비교 가능한 권역의 민심 급지는 그대로예요.";
			});
			return;
		}
		rises.sort(CHANGE_ORDER);
		falls.sort(CHANGE_ORDER);
		List<Change> topRises = rises.stream().limit(5).toList();
		List<Change> topFalls = falls.stream().limit(5).toList();
		long middleRises = topRises.stream().filter(change -> change.previousTier().intValue() >= 3)
			.count();
		long upperFalls = topFalls.stream().filter(change -> change.previousTier().intValue() <= 2)
			.count();
		if (topRises.size() >= 3 && topFalls.size() >= 3
			&& middleRises * 2 > topRises.size() && upperFalls * 2 > topFalls.size()) {
			sentences.add(switch (variant) {
				case 0 -> "지난 리포트와 비교하면 중간 급지가 올라오고 위쪽 급지가 내려오는 흐름이에요.";
				case 1 -> "이번 주에는 이전 3~5급지 권역의 상승과 1~2급지 권역의 하락이 두드러져요.";
				default -> "지난주보다 중간 급지 권역은 오르고 위쪽 급지 권역은 내리는 흐름이 나타났어요.";
			});
		} else {
			sentences.add(switch (variant) {
				case 0 -> "지난 리포트와 비교해 민심 급지가 오른 권역은 " + rises.size()
					+ "곳, 내린 권역은 " + falls.size() + "곳이에요.";
				case 1 -> "이번 주 민심 급지는 " + rises.size() + "곳에서 올랐고 "
					+ falls.size() + "곳에서 내렸어요.";
				default -> "지난 리포트 대비 " + rises.size() + "곳은 올랐고 "
					+ falls.size() + "곳은 내렸어요.";
			});
		}
		List<String> distribution = new ArrayList<>();
		if (!topRises.isEmpty()) {
			distribution.add("많이 오른 " + topRises.size() + "곳 중 " + middleRises
				+ "곳이 이전 기준 3~5급지");
		}
		if (!topFalls.isEmpty()) {
			distribution.add("많이 내린 " + topFalls.size() + "곳 중 " + upperFalls
				+ "곳이 이전 기준 1~2급지");
		}
		sentences.add(String.join(", ", distribution) + "예요.");
	}

	private void appendInitialComparison(List<String> sentences, List<Zone> current,
		int variant) {
		List<InitialDifference> differences = current.stream()
			.filter(zone -> zone.rankingBasis() == RankingBasis.VOTE && zone.initialTier() != null)
			.map(zone -> new InitialDifference(zone,
				zone.voteTier().subtract(zone.initialTier()).abs()))
			.filter(difference -> difference.delta().signum() > 0)
			.sorted(Comparator.comparing(InitialDifference::delta, Comparator.reverseOrder())
				.thenComparing(difference -> difference.zone().zoneName())
				.thenComparing(difference -> difference.zone().zoneId()))
			.toList();
		if (differences.isEmpty()) {
			return;
		}
		BigDecimal largest = differences.getFirst().delta();
		int direction = differences.getFirst().zone().voteTier()
			.compareTo(differences.getFirst().zone().initialTier());
		List<InitialDifference> sameLargestAndDirection = differences.stream()
			.filter(difference -> difference.delta().compareTo(largest) == 0
				&& Integer.signum(difference.zone().voteTier()
					.compareTo(difference.zone().initialTier())) == Integer.signum(direction))
			.toList();
		String names = sameLargestAndDirection.stream().limit(2)
			.map(difference -> difference.zone().zoneName())
			.reduce((first, second) -> first + "·" + second).orElseThrow();
		if (sameLargestAndDirection.size() > 2) {
			names += " 등";
		}
		String directionText = direction < 0 ? "높아요." : "낮아요.";
		sentences.add(switch (variant) {
			case 0 -> "초기 급지와 가장 큰 차이를 보인 권역 중 " + names
				+ "의 민심 급지는 초기 급지보다 " + largest.toPlainString() + "급지 "
				+ directionText;
			case 1 -> "초기 급지와 비교하면 " + names + "의 차이가 가장 커요. 민심 급지는 "
				+ largest.toPlainString() + "급지 " + directionText;
			default -> "초기 급지에서 가장 멀어진 권역 중 " + names
				+ "의 민심 급지는 " + largest.toPlainString() + "급지 " + directionText;
		});
	}

	private record Change(Zone current, BigDecimal previousTier, BigDecimal delta) {
	}

	private record InitialDifference(Zone zone, BigDecimal delta) {
	}
}
