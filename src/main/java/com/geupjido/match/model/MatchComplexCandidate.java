package com.geupjido.match.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 대진에 참여할 활성 단지와 카드 표시값. */
public record MatchComplexCandidate(
	String complexId,
	String zoneId,
	String name,
	String cityName,
	String zoneName,
	BigDecimal zoneTier,
	int households,
	LocalDate approvalDate,
	BigDecimal representativeArea,
	int priceTenThousandWon,
	LocalDate lastDealDate
) {
}
