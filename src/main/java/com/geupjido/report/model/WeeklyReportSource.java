package com.geupjido.report.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.geupjido.zone.domain.RankingBasis;

public final class WeeklyReportSource {
	private WeeklyReportSource() {
	}

	public record Report(long id, Instant periodStartAt, Instant periodEndAt,
		Instant cutoffCompletedAt, Instant publishedAt, long validVoteCount,
		long participantCount, Integer changedGradeZoneCount, String summaryText) {
	}

	public record Zone(String zoneId, String zoneName, String cityId, String cityName,
		String regionCode, String regionName, RankingBasis rankingBasis,
		BigDecimal initialTier, BigDecimal voteTier, int validVoteCount, int participantCount,
		int crossZoneVoteCount) {
	}

	public record ZoneIdentity(String zoneId, String zoneName, String cityId,
		String cityName, String regionCode, String regionName, BigDecimal initialTier) {
	}

	public record CollectingProgress(String zoneId, String zoneName,
		String cityName, String regionName, int validVoteCount,
		int participantCount, int crossZoneVoteCount) {
	}
}
