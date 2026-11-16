package com.geupjido.report.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.geupjido.report.model.WeeklyReportPeriod;
import com.geupjido.report.model.WeeklyReportSource.Report;
import com.geupjido.report.model.WeeklyReportSource.CollectingProgress;
import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.report.model.WeeklyReportSource.ZoneIdentity;

public interface WeeklyReportRepository {
	void lockPublication();

	boolean existsForPeriod(Instant periodEndAt);

	boolean hasDraftForPeriod(Instant periodEndAt);

	boolean hasFailedAttempt(Instant periodEndAt);

	void recordFailedAttempt(Instant periodEndAt, Instant failedAt);

	void clearFailedAttempt(Instant periodEndAt);

	List<ZoneIdentity> currentZones();

	List<CollectingProgress> currentCollectingZones();

	Optional<Report> previousPublished(Instant periodEndAt);

	List<Report> publishedReports();

	Optional<Report> publishedReport(long id);

	List<Zone> zones(long reportId);

	void saveDraft(WeeklyReportPeriod period, Instant completedAt, long validVotes,
		long participants, Integer changedGradeZones, String summary,
		List<Zone> zones);

	boolean publish(Instant periodEndAt, Instant publishedAt);
}
