package com.geupjido.ranking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.zone.domain.RankingBasis;

public final class RankingView {
	private RankingView() {
	}

	public enum Region {
		ALL, SEOUL, GYEONGGI_INCHEON
	}

	public enum Tab {
		GRADE_1, GRADE_2, GRADE_3, GRADE_4, GRADE_5, COLLECTING
	}

	public enum Sort {
		CURRENT, RISEN, FALLEN
	}

	public enum Movement {
		UP, DOWN, SAME, NONE
	}

	@Schema(name = "RankingGradeCount")
	public record GradeCount(int grade, long count) {
	}

	@Schema(name = "RankingZone", description = "랭킹 탭의 권역별 순위와 변동")
	public record Zone(String zoneId, String name, String cityName, String regionName,
		RankingBasis rankingBasis, BigDecimal tier, Long rank, long rankTotal,
		BigDecimal previousTier, BigDecimal tierDelta, Movement movement,
		int validVoteCount, int participantCount, int crossZoneVoteCount) {
	}

	public record Response(Region region, Tab tab, Sort sort, long totalZoneCount,
		long rankedZoneCount, List<GradeCount> gradeCounts, long collectingCount,
		Instant lastCompletedAt, Instant previousCompletedAt, List<Zone> zones) {
		public Response {
			gradeCounts = List.copyOf(gradeCounts);
			zones = List.copyOf(zones);
		}
	}
}
