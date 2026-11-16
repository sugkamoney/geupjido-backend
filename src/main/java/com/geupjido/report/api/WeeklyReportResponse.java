package com.geupjido.report.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.zone.domain.RankingBasis;

public final class WeeklyReportResponse {
	private WeeklyReportResponse() {
	}

	public enum State {
		COLLECTING, PREPARING, DELAYED, PUBLISHED
	}

	public enum Movement {
		UP, DOWN
	}

	@Schema(name = "WeeklyReportSummary")
	public record Summary(long reportId, Instant periodStart, Instant periodEnd,
		Instant publishedAt, boolean latest) {
	}

	@Schema(name = "WeeklyReportListResponse")
	public record ListResponse(State state, Instant expectedReleaseAt,
		List<Summary> reports, List<CollectingZone> collectingZones) {
		public ListResponse {
			reports = List.copyOf(reports);
			collectingZones = List.copyOf(collectingZones);
		}
	}

	@Schema(name = "WeeklyReportZoneChange")
	public record ZoneChange(String zoneId, String zoneName, String cityName,
		String regionName, BigDecimal previousTier, BigDecimal currentTier,
		BigDecimal tierDelta) {
	}

	@Schema(name = "WeeklyReportBoundaryZone")
	public record BoundaryZone(String zoneId, String zoneName, String cityName,
		BigDecimal currentTier, BigDecimal upperGradeBoundary,
		BigDecimal distance) {
	}

	@Schema(name = "WeeklyReportCollectingZone")
	public record CollectingZone(String zoneId, String zoneName, String cityName,
		String regionName, RankingBasis rankingBasis, int validVoteCount,
		int participantCount, int crossZoneVoteCount,
		int requiredValidVoteCount, int requiredParticipantCount,
		int requiredCrossZoneVoteCount) {
	}

	@Schema(name = "WeeklyReportCityChange")
	public record CityChange(String cityId, String cityName, String regionName,
		BigDecimal previousAverageTier, BigDecimal currentAverageTier,
		BigDecimal averageTierDelta, Movement movement, int comparedZoneCount) {
	}

	@Schema(name = "WeeklyReportDetail")
	public record Detail(long reportId, Instant periodStart, Instant periodEnd,
		Instant publishedAt, Instant cutoffCompletedAt, long participantCount,
		long validVoteCount, Integer changedGradeZoneCount, String summary,
		Long previousReportId, Long nextReportId,
		List<ZoneChange> risenZones, List<ZoneChange> fallenZones,
		List<BoundaryZone> nearBoundaryZones,
		List<CollectingZone> collectingZones,
		List<CityChange> cityChanges) {
		public Detail {
			risenZones = List.copyOf(risenZones);
			fallenZones = List.copyOf(fallenZones);
			nearBoundaryZones = List.copyOf(nearBoundaryZones);
			collectingZones = List.copyOf(collectingZones);
			cityChanges = List.copyOf(cityChanges);
		}
	}
}
