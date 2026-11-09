package com.geupjido.match.repository;

import java.time.Instant;

import com.geupjido.match.service.WorldcupVoteRatingService.Calculation;

public interface WorldcupVoteRatingPublicationRepository {

	void lockPublication();

	void publish(Calculation calculation, Instant completedAt);
}
