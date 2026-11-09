package com.geupjido.match.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.geupjido.match.model.WorldcupZoneRanking;
import com.geupjido.zone.domain.RankingBasis;

public record WorldcupZoneRankingResponse(
	String zoneId,
	String zoneName,
	BigDecimal tier,
	RankingBasis rankingBasis,
	Instant lastCompletedAt,
	List<WorldcupZoneRanking.ComplexRank> complexes
) {
	public static WorldcupZoneRankingResponse from(WorldcupZoneRanking ranking) {
		return new WorldcupZoneRankingResponse(
			ranking.zoneId(), ranking.zoneName(), ranking.tier(),
			ranking.rankingBasis(), ranking.lastCompletedAt(), ranking.complexes()
		);
	}
}
