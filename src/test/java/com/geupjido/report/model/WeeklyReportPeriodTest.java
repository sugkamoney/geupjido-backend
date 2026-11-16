package com.geupjido.report.model;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyReportPeriodTest {
	@Test
	void 일요일_18시_전에는_지난주_마감을_가리킨다() {
		var period = WeeklyReportPeriod.latestClosedAt(
			Instant.parse("2026-11-15T08:59:59Z"));
		assertThat(period.endAt()).isEqualTo(Instant.parse("2026-11-08T09:00:00Z"));
	}

	@Test
	void 일요일_18시부터_이번주_마감과_21시_공개시각을_가리킨다() {
		var period = WeeklyReportPeriod.latestClosedAt(
			Instant.parse("2026-11-15T09:00:00Z"));
		assertThat(period.startAt()).isEqualTo(Instant.parse("2026-11-08T09:00:00Z"));
		assertThat(period.endAt()).isEqualTo(Instant.parse("2026-11-15T09:00:00Z"));
		assertThat(period.releaseAt()).isEqualTo(Instant.parse("2026-11-15T12:00:00Z"));
	}

	@Test
	void 다음주_평일에도_가장_최근_일요일_마감을_유지한다() {
		var period = WeeklyReportPeriod.latestClosedAt(
			Instant.parse("2026-11-16T07:00:00Z"));
		assertThat(period.endAt()).isEqualTo(Instant.parse("2026-11-15T09:00:00Z"));
	}
}
