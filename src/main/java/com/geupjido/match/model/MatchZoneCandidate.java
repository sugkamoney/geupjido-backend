package com.geupjido.match.model;

import java.math.BigDecimal;

/** 대진의 기준·상대 권역을 고를 때 필요한 읽기 전용 정보. */
public record MatchZoneCandidate(
	String zoneId,
	String cityId,
	String zoneName,
	BigDecimal tier,
	double longitude,
	double latitude,
	long activeComplexCount
) {
}
