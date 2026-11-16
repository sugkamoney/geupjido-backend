package com.geupjido.report.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static java.time.temporal.TemporalAdjusters.previousOrSame;

/** 가장 최근의 일요일 18시 마감과 해당 리포트의 공개 가능 시각. */
public record WeeklyReportPeriod(Instant startAt, Instant endAt, Instant releaseAt) {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final LocalTime CUTOFF_TIME = LocalTime.of(18, 0);

	public static WeeklyReportPeriod latestClosedAt(Instant now) {
		ZonedDateTime localNow = now.atZone(SEOUL);
		LocalDate sunday = localNow.toLocalDate().with(previousOrSame(DayOfWeek.SUNDAY));
		ZonedDateTime end = sunday.atTime(CUTOFF_TIME).atZone(SEOUL);
		if (end.toInstant().isAfter(now)) {
			end = end.minusWeeks(1);
		}
		return new WeeklyReportPeriod(end.minusWeeks(1).toInstant(),
			end.toInstant(), end.plusHours(3).toInstant());
	}
}
