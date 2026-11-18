package com.geupjido.match.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.WorldcupGameState;
import com.geupjido.match.model.WorldcupGameMode;

public interface WorldcupGameRepository {

	void lockAnonymousId(UUID anonymousId);

	default WorldcupGameState save(UUID anonymousId, GeneratedMatchGame game, Instant startedAt) {
		return save(anonymousId, game, startedAt, WorldcupGameMode.GENERAL);
	}

	WorldcupGameState save(UUID anonymousId, GeneratedMatchGame game, Instant startedAt,
		WorldcupGameMode mode);

	Optional<WorldcupGameState> findLatestByAnonymousId(UUID anonymousId);

	Optional<WorldcupGameState> findLatestByAnonymousIdForUpdate(UUID anonymousId);

	Optional<WorldcupGameState> findLatest(UUID anonymousId, WorldcupGameMode mode,
		String zoneId, Instant startedSince, Instant startedBefore, boolean forUpdate);

	long countStartedByAnonymousId(UUID anonymousId, Instant fromInclusive, Instant toExclusive);

	long countCompletedRounds(UUID anonymousId, Instant fromInclusive, Instant toExclusive);

	void saveVote(long gameId, int roundNumber, String leftComplexId,
		String rightComplexId, String selectedComplexId);

	void saveSkip(long gameId, int roundNumber);

	void complete(long gameId);
}
