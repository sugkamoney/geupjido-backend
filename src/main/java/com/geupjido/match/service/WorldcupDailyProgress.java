package com.geupjido.match.service;

import java.time.LocalDate;

public record WorldcupDailyProgress(LocalDate date, int dailyLimit,
	int completedRounds, int remainingRounds, Integer nextMilestone,
	Integer roundsUntilNextMilestone) {
}
