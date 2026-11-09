package com.geupjido.match.model;

import java.time.Instant;
import java.util.List;

/** 저장된 대진과 각 판에서 확정된 선택을 함께 읽은 게임 상태. */
public record WorldcupGameState(
	long id,
	GeneratedMatchGame game,
	List<RecordedVote> votes,
	List<RecordedSkip> skips,
	Instant completedAt
) {
	public WorldcupGameState {
		votes = List.copyOf(votes);
		skips = List.copyOf(skips);
	}

	public boolean completed() {
		return completedAt != null;
	}

	public Integer nextRoundNumber() {
		return completed() ? null : votes.size() + skips.size() + 1;
	}
}
