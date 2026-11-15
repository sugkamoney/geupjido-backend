package com.geupjido.ranking.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.geupjido.zone.domain.RankingBasis;

public final class RankingSource {
	private RankingSource() {
	}

	public record Zone(String id, String name, String cityName, String regionName,
		BigDecimal initialTier, BigDecimal voteTier, BigDecimal voteScore,
		RankingBasis rankingBasis, BigDecimal previousPublishedTier,
		int validVoteCount, int participantCount, int crossZoneVoteCount) {
	}

	public record Publication(Instant lastCompletedAt, Instant previousCompletedAt) {
	}
}
