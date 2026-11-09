package com.geupjido.match.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import io.swagger.v3.oas.annotations.media.Schema;

import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.model.RecordedSkip;
import com.geupjido.match.model.RecordedVote;
import com.geupjido.match.model.WorldcupPairRatio;
import com.geupjido.match.model.WorldcupGameState;

@Schema(description = "순서와 카드 값이 고정된 월드컵 게임 10판")
public record WorldcupGameResponse(
	String baseZoneId,
	BigDecimal tierRangeUsed,
	List<Round> matchups,
	List<Selection> selections,
	List<Skip> skips,
	Integer nextRoundNumber,
	boolean completed,
	RoundResult roundResult
) {
	public static WorldcupGameResponse from(WorldcupGameState state) {
		return from(state, null);
	}

	public static WorldcupGameResponse from(WorldcupGameState state, RoundResult roundResult) {
		List<Round> rounds = IntStream.range(0, state.game().matchups().size())
			.mapToObj(index -> Round.from(index + 1, state.game().matchups().get(index)))
			.toList();
		return new WorldcupGameResponse(
			state.game().baseZoneId(), state.game().tierRangeUsed(), rounds,
			state.votes().stream().map(Selection::from).toList(),
			state.skips().stream().map(Skip::from).toList(),
			state.nextRoundNumber(), state.completed(), roundResult
		);
	}

	public record RoundResult(int roundNumber, boolean available,
		Integer leftPercent, Integer rightPercent) {
		public static RoundResult from(int roundNumber, Optional<WorldcupPairRatio> ratio) {
			return ratio.map(value -> new RoundResult(roundNumber, true,
				value.leftPercent(), value.rightPercent()))
				.orElseGet(() -> new RoundResult(roundNumber, false, null, null));
		}
	}

	public record Skip(int roundNumber, Instant skippedAt) {
		private static Skip from(RecordedSkip skip) {
			return new Skip(skip.roundNumber(), skip.skippedAt());
		}
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
