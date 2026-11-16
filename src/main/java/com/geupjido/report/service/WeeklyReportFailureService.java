package com.geupjido.report.service;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.report.model.WeeklyReportPeriod;
import com.geupjido.report.repository.WeeklyReportRepository;

@Service
public class WeeklyReportFailureService {
	private final WeeklyReportRepository repository;
	private final Clock clock;

	public WeeklyReportFailureService(WeeklyReportRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordLatestFailure() {
		var now = clock.instant();
		var period = WeeklyReportPeriod.latestClosedAt(now);
		if (!repository.existsForPeriod(period.endAt())) {
			repository.recordFailedAttempt(period.endAt(), now);
		}
	}
}
