package com.geupjido.match.api;

import java.time.LocalDate;

import com.geupjido.match.service.WorldcupDailyProgress;

public record WorldcupDailyProgressResponse(LocalDate date, int dailyLimit,
	int completedRounds, int remainingRounds, Integer nextMilestone,
	Integer roundsUntilNextMilestone) {
	public static WorldcupDailyProgressResponse from(WorldcupDailyProgress progress) {
		return new WorldcupDailyProgressResponse(progress.date(), progress.dailyLimit(),
			progress.completedRounds(), progress.remainingRounds(),
			progress.nextMilestone(), progress.roundsUntilNextMilestone());
	}
}
