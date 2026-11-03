package com.geupjido.match.repository;

import java.util.Optional;
import java.util.UUID;

import com.geupjido.match.model.GeneratedMatchGame;

public interface WorldcupGameRepository {

	void save(UUID anonymousId, GeneratedMatchGame game);

	Optional<GeneratedMatchGame> findLatestByAnonymousId(UUID anonymousId);
}
