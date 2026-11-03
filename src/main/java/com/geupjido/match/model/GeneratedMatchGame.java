package com.geupjido.match.model;

import java.math.BigDecimal;
import java.util.List;

/** 게임 시작 시 고정하는 기준 권역과 10개 대진. */
public record GeneratedMatchGame(
	String baseZoneId,
	BigDecimal tierRangeUsed,
	List<Matchup> matchups
) {
	public GeneratedMatchGame {
		matchups = List.copyOf(matchups);
	}
}
