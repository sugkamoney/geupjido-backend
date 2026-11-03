package com.geupjido.match.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.Matchup;

@Schema(description = "순서와 카드 값이 고정된 월드컵 게임 10판")
public record WorldcupGameResponse(
	String baseZoneId,
	BigDecimal tierRangeUsed,
	List<Round> matchups
) {
	public static WorldcupGameResponse from(GeneratedMatchGame game) {
		List<Round> rounds = IntStream.range(0, game.matchups().size())
			.mapToObj(index -> Round.from(index + 1, game.matchups().get(index)))
			.toList();
		return new WorldcupGameResponse(game.baseZoneId(), game.tierRangeUsed(), rounds);
	}

	public record Round(
		int roundNumber,
		MatchType type,
		MatchCard left,
		MatchCard right
	) {
		private static Round from(int number, Matchup matchup) {
			return new Round(number, matchup.type(), matchup.left(), matchup.right());
		}
	}
}
