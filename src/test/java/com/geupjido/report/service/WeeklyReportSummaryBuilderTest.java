package com.geupjido.report.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.zone.domain.RankingBasis;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportSummaryBuilderTest {
	private static final Instant WEEK_ZERO = Instant.parse("2026-11-15T09:00:00Z");
	private final WeeklyReportSummaryBuilder builder = new WeeklyReportSummaryBuilder();

	@Test
	void 상위_변화의_급지_분포와_초기_급지_차이로_시안형_요약을_만든다() {
		List<Zone> previous = new ArrayList<>();
		List<Zone> current = new ArrayList<>();
		add(previous, current, "불광", "3.4", "2.2", "3.4");
		add(previous, current, "망우", "4.5", "3.3", "4.5");
		add(previous, current, "중간", "5.1", "4.9", "5.1");
		add(previous, current, "위쪽", "2.1", "2.0", "2.1");
		add(previous, current, "추가", "1.8", "1.7", "1.8");
		add(previous, current, "하락1", "1.1", "1.2", "1.1");
		add(previous, current, "하락2", "1.3", "1.4", "1.3");
		add(previous, current, "하락3", "2.1", "2.2", "2.1");
		add(previous, current, "하락4", "2.5", "2.6", "2.5");
		add(previous, current, "하락5", "3.2", "3.3", "3.2");

		String summary = builder.build(WEEK_ZERO, true, previous, current);

		assertThat(summary).contains("중간 급지가 올라오고 위쪽 급지가 내려오는 흐름")
			.contains("많이 오른 5곳 중 3곳이 이전 기준 3~5급지")
			.contains("많이 내린 5곳 중 4곳이 이전 기준 1~2급지")
			.contains("망우·불광의 민심 급지는 초기 급지보다 1.2급지 높아요");
	}

	@Test
	void 첫_리포트에서는_전주_비교를_하지_않고_초기_급지_차이만_말한다() {
		String summary = builder.build(WEEK_ZERO, false, List.of(),
			List.of(zone("a", "성수", "2.2", "1.9")));

		assertThat(summary).startsWith("첫 민심 급지 리포트예요.")
			.contains("초기 급지보다 0.3급지 높아요.")
			.doesNotContain("지난 리포트");
	}

	@Test
	void 변화가_없거나_초기_급지가_없으면_추측하는_문장을_쓰지_않는다() {
		Zone unchanged = zone("a", "성수", null, "2.0");
		String summary = builder.build(WEEK_ZERO, true, List.of(unchanged), List.of(unchanged));

		assertThat(summary).contains("민심 급지가 변한 권역은 없어요")
			.doesNotContain("초기 급지와");
	}

	@Test
	void 주가_달라지면_문장_틀은_바뀌지만_같은_수치를_말한다() {
		Zone old = zone("a", "성수", "2.2", "2.2");
		Zone current = zone("a", "성수", "2.2", "1.9");
		String first = builder.build(WEEK_ZERO, true, List.of(old), List.of(current));
		String second = builder.build(WEEK_ZERO.plusSeconds(604_800), true,
			List.of(old), List.of(current));
		String third = builder.build(WEEK_ZERO.plusSeconds(1_209_600), true,
			List.of(old), List.of(current));

		assertThat(first).isNotEqualTo(second).isNotEqualTo(third);
		assertThat(second).isNotEqualTo(third);
		assertThat(List.of(first, second, third)).allSatisfy(summary ->
			assertThat(summary).contains("1곳").contains("0.3급지").contains("높아요"));
		assertThat(builder.build(WEEK_ZERO, true, List.of(old), List.of(current)))
			.isEqualTo(first);
	}

	private static void add(List<Zone> previous, List<Zone> current,
		String name, String oldTier, String newTier, String initialTier) {
		previous.add(zone(name, name, initialTier, oldTier));
		current.add(zone(name, name, initialTier, newTier));
	}

	private static Zone zone(String id, String name, String initialTier,
		String voteTier) {
		return new Zone(id, name, "city", "구", "seoul", "서울", RankingBasis.VOTE,
			decimal(initialTier), decimal(voteTier), 60, 10, 20);
	}

	private static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
