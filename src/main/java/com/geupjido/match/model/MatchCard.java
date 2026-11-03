package com.geupjido.match.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 월드컵 카드에 표시할 단지 정보. */
public record MatchCard(
	String complexId,
	String name,
	String cityName,
	String zoneName,
	BigDecimal zoneTier,
	int households,
	int approvalYear,
	RepresentativeSale representativeSale
) {
	public record RepresentativeSale(
		BigDecimal exclusiveAreaSquareMeters,
		int priceTenThousandWon,
		LocalDate contractDate
	) {
	}

	public static MatchCard from(MatchComplexCandidate candidate) {
		return new MatchCard(
			candidate.complexId(),
			candidate.name(),
			candidate.cityName(),
			candidate.zoneName(),
			candidate.zoneTier(),
			candidate.households(),
			candidate.approvalDate().getYear(),
			new RepresentativeSale(
				candidate.representativeArea(),
				candidate.priceTenThousandWon(),
				candidate.lastDealDate()
			)
		);
	}
}
