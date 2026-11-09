package com.geupjido.match.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.geupjido.zone.domain.RankingBasis;

public record WorldcupZoneRanking(
	String zoneId,
	String zoneName,
	BigDecimal tier,
	RankingBasis rankingBasis,
	Instant lastCompletedAt,
	List<ComplexRank> complexes
) {
	public WorldcupZoneRanking {
		complexes = List.copyOf(complexes);
	}

	public record ComplexRank(
		String complexId,
		String name,
		Integer zoneRank,
		BigDecimal score,
		Integer price,
		BigDecimal representativeArea
	) {
	}
}
