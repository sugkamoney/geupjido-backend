package com.geupjido.match.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.repository.WorldcupVoteRatingPublicationRepository;

@Service
public class WorldcupVoteRatingPublicationService {

	private final WorldcupVoteRatingService ratingService;
	private final WorldcupVoteRatingPublicationRepository publicationRepository;
	private final Clock clock;

	public WorldcupVoteRatingPublicationService(
		WorldcupVoteRatingService ratingService,
		WorldcupVoteRatingPublicationRepository publicationRepository,
		Clock clock
	) {
		this.ratingService = ratingService;
		this.publicationRepository = publicationRepository;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public Instant publish() {
		publicationRepository.lockPublication();
		var calculation = ratingService.calculate();
		Instant completedAt = clock.instant();
		publicationRepository.publish(calculation, completedAt);
		return completedAt;
	}
}
