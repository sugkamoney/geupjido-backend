package com.geupjido.match.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.WorldcupGameState;

public interface WorldcupGameRepository {

	void lockAnonymousId(UUID anonymousId);

	WorldcupGameState save(UUID anonymousId, GeneratedMatchGame game, Instant startedAt);

	Optional<WorldcupGameState> findLatestByAnonymousId(UUID anonymousId);

	Optional<WorldcupGameState> findLatestByAnonymousIdForUpdate(UUID anonymousId);

	long countStartedByAnonymousId(UUID anonymousId, Instant fromInclusive, Instant toExclusive);

	void saveVote(long gameId, int roundNumber, String leftComplexId,
		String rightComplexId, String selectedComplexId);

	void complete(long gameId);
}
