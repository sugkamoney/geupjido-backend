package com.geupjido.match.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.WorldcupGameState;

@Schema(description = "순서와 카드 값이 고정된 월드컵 게임 10판")
public record WorldcupGameResponse(
	String baseZoneId,
	BigDecimal tierRangeUsed,
	List<Round> matchups,
	List<Selection> selections,
	Integer nextRoundNumber,
	boolean completed
) {
	public static WorldcupGameResponse from(WorldcupGameState state) {
		List<Round> rounds = IntStream.range(0, state.game().matchups().size())
			.mapToObj(index -> Round.from(index + 1, state.game().matchups().get(index)))
			.toList();
		return new WorldcupGameResponse(
			state.game().baseZoneId(), state.game().tierRangeUsed(), rounds,
			state.votes().stream().map(Selection::from).toList(),
			state.nextRoundNumber(), state.completed()
		);
	}

	public record Selection(int roundNumber, String selectedComplexId, Instant selectedAt) {
		private static Selection from(RecordedVote vote) {
			return new Selection(vote.roundNumber(), vote.selectedComplexId(), vote.selectedAt());
		}
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
