package com.geupjido.match.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.repository.WorldcupGameRepository;

@Service
public class WorldcupDailyProgressService {

	private static final int DAILY_LIMIT = 60;
	private final WorldcupGameRepository repository;
	private final WorldcupDailyGameLimit dailyGameLimit;

	public WorldcupDailyProgressService(WorldcupGameRepository repository,
		WorldcupDailyGameLimit dailyGameLimit) {
		this.repository = repository;
		this.dailyGameLimit = dailyGameLimit;
	}

	@Transactional(readOnly = true)
	public WorldcupDailyProgress get(UUID anonymousId) {
		WorldcupDailyGameLimit.Day day = dailyGameLimit.currentDay();
		long completed = anonymousId == null ? 0 : repository.countCompletedRounds(
			anonymousId, day.fromInclusive(), day.toExclusive());
		if (completed > DAILY_LIMIT) {
			throw new IllegalStateException("하루 일반 월드컵 진행 판 수가 한도를 초과했습니다.");
		}
		int rounds = (int) completed;
		Integer milestone = null;
		if (rounds < 30) {
			milestone = 30;
		} else if (rounds < 60) {
			milestone = 60;
		}
		return new WorldcupDailyProgress(day.date(), DAILY_LIMIT, rounds,
			DAILY_LIMIT - rounds, milestone, milestone == null ? null : milestone - rounds);
	}
}
