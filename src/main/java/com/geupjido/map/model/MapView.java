package com.geupjido.map.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.geupjido.zone.domain.RankingBasis;

/** DB 엔티티를 그대로 노출하지 않고 지도 조회에 필요한 값만 전달하는 읽기 모델. */
public final class MapView {
	private MapView() {
	}

	public record Point(double longitude, double latitude) {
	}

	public record City(String id, String regionCode, String regionName, String name,
		Point center, Bounds bounds, long zoneCount, BigDecimal initialTier,
		BigDecimal voteTier) {
	}

	/** initialTier는 편집한 초기값이고, voteTier는 민심 집계가 발행되기 전에는 null이다. */
	public record Zone(String id, String cityId, String cityName, String regionCode,
		String name, Point center, BigDecimal initialTier, BigDecimal voteTier,
		RankingBasis rankingBasis, long complexCount, int validVoteCount) {
	}

	public record ComplexPin(String id, String zoneId, String cityName,
		String zoneName, String name, Point location, int households) {
	}

	/** voteCandidate=false인 단지도 지도에는 보이지만 투표 순위는 없을 수 있다. */
	public record Complex(String id, String name, Integer zoneRank, BigDecimal score,
		Integer price, BigDecimal representativeArea, LocalDate lastDealDate,
		LocalDate approvalDate, int households, boolean voteCandidate) {
	}

	public record ZoneDetail(Zone zone, String polygonGeoJson, List<Complex> complexes) {
		public ZoneDetail {
			complexes = List.copyOf(complexes);
		}
	}

	public record SearchResult(List<City> cities, List<Zone> zones,
		List<ComplexPin> complexes) {
	}

	/** 표와 참여자 수는 마지막 집계 발행 시점의 값이며 즉시 갱신되는 값이 아니다. */
	public record Status(long zoneCount, long voteBasedZoneCount,
		long validVoteCount, long participantCount, Instant lastCompletedAt,
		Instant nextScheduledAt) {
	}

	public record Bounds(double west, double south, double east, double north) {
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
