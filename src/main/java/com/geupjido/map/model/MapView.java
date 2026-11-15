package com.geupjido.map.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.zone.domain.RankingBasis;

/** DB 엔티티를 그대로 노출하지 않고 지도 조회에 필요한 값만 전달하는 읽기 모델. */
public final class MapView {
	private MapView() {
	}

	public record Point(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double longitude,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double latitude) {
	}

	public record City(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String regionCode,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String regionName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Point center,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Bounds bounds,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long zoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long initialTierZoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voteTierZoneCount,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal initialTier,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal voteTier) {
	}

	/** initialTier는 편집한 초기값이고, voteTier는 민심 집계가 발행되기 전에는 null이다. */
	@Schema(name = "MapZone", description = "지도 목록·검색·상세에서 공통으로 사용하는 권역")
	public record Zone(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String regionCode,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String regionName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Point center,
		@Schema(description = "실제 권역 경계의 면적(㎡)", minimum = "0",
			requiredMode = Schema.RequiredMode.REQUIRED)
		double areaSquareMeters,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal initialTier,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal voteTier,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) RankingBasis rankingBasis,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long initialRank,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long initialRankTotal,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long voteRank,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voteRankTotal,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long complexCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int validVoteCount) {
	}

	public record ComplexPin(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String zoneId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String cityName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String zoneName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Point location,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int households) {
	}

	/** voteCandidate=false인 단지도 지도에는 보이지만 투표 순위는 없을 수 있다. */
	@Schema(name = "MapComplex", description = "권역 상세의 단지 정보")
	public record Complex(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer zoneRank,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal score,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Integer price,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
		BigDecimal representativeArea,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate lastDealDate,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate approvalDate,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int households,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean voteCandidate,
		@Schema(description = "WGS84 단지 위치. 위치 미확인 시 null",
			nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Point location) {
	}

	public record ZoneDetail(Zone zone, String polygonGeoJson, List<Complex> complexes) {
		public ZoneDetail {
			complexes = List.copyOf(complexes);
		}
	}

	public record SearchResult(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<City> cities,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Zone> zones,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ComplexPin> complexes) {
	}

	/** 표와 참여자 수는 마지막 집계 발행 시점의 값이며 즉시 갱신되는 값이 아니다. */
	@Schema(name = "MapGradeCount")
	public record GradeCount(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int grade,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
	}

	public record Status(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long zoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long initialTierZoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long missingInitialTierZoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long voteBasedZoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long collectingZoneCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<GradeCount> initialGradeCounts,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long validVoteCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long participantCount,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant lastCompletedAt,
		@Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Instant nextScheduledAt) {
		public Status {
			initialGradeCounts = List.copyOf(initialGradeCounts);
		}
	}

	public record Bounds(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double west,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double south,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double east,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) double north) {
		public Bounds {
			// west/east는 경도, south/north는 위도다. 대한민국 지도는 날짜변경선을
			// 넘지 않으므로 이 API는 west < east인 일반 사각형만 받는다.
			if (!Double.isFinite(west) || !Double.isFinite(south)
				|| !Double.isFinite(east) || !Double.isFinite(north)
				|| west < -180 || east > 180 || south < -90 || north > 90
				|| west >= east || south >= north) {
				throw new IllegalArgumentException("올바른 지도 범위를 입력해 주세요.");
			}
		}
	}
}
