package com.geupjido.report.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.service.WorldcupVoteEligibilityPolicy;
import com.geupjido.report.api.WeeklyReportResponse;
import com.geupjido.report.api.WeeklyReportResponse.BoundaryZone;
import com.geupjido.report.api.WeeklyReportResponse.CityChange;
import com.geupjido.report.api.WeeklyReportResponse.CollectingZone;
import com.geupjido.report.api.WeeklyReportResponse.Movement;
import com.geupjido.report.api.WeeklyReportResponse.ZoneChange;
import com.geupjido.report.model.WeeklyReportPeriod;
import com.geupjido.report.model.WeeklyReportSource.Report;
import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.report.repository.WeeklyReportRepository;
import com.geupjido.zone.domain.RankingBasis;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class WeeklyReportQueryService {
	private static final BigDecimal TENTH = new BigDecimal("0.1");
	private static final Duration WEEK = Duration.ofDays(7);
	private static final Comparator<ZoneChange> CHANGE_ORDER = Comparator
		.comparing(ZoneChange::tierDelta, Comparator.reverseOrder())
		.thenComparing(ZoneChange::zoneName).thenComparing(ZoneChange::zoneId);
	private final WeeklyReportRepository repository;
	private final Clock clock;

	public WeeklyReportQueryService(WeeklyReportRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public WeeklyReportResponse.ListResponse list() {
		Instant now = clock.instant();
		WeeklyReportPeriod period = WeeklyReportPeriod.latestClosedAt(now);
		List<Report> published = repository.publishedReports();
		boolean draft = repository.hasDraftForPeriod(period.endAt());
		boolean currentPublished = !published.isEmpty()
			&& published.getFirst().periodEndAt().equals(period.endAt());
		WeeklyReportResponse.State state;
		if (currentPublished) {
			state = WeeklyReportResponse.State.PUBLISHED;
		} else if (draft || !published.isEmpty()
			|| repository.hasFailedAttempt(period.endAt())) {
			state = now.isBefore(period.releaseAt())
				? WeeklyReportResponse.State.PREPARING : WeeklyReportResponse.State.DELAYED;
		} else {
			state = WeeklyReportResponse.State.COLLECTING;
		}
		Instant expectedRelease = currentPublished
			|| (state == WeeklyReportResponse.State.COLLECTING
				&& !now.isBefore(period.releaseAt()))
			? period.releaseAt().plus(WEEK) : period.releaseAt();
		List<WeeklyReportResponse.Summary> items = new ArrayList<>();
		for (int index = 0; index < published.size(); index++) {
			Report report = published.get(index);
			items.add(new WeeklyReportResponse.Summary(report.id(),
				report.periodStartAt(), report.periodEndAt(), report.publishedAt(), index == 0));
		}
		List<CollectingZone> currentCollecting = published.isEmpty()
			? repository.currentCollectingZones().stream()
				.map(progress -> new CollectingZone(progress.zoneId(), progress.zoneName(),
					progress.cityName(), progress.regionName(), RankingBasis.INITIAL,
					progress.validVoteCount(), progress.participantCount(),
					progress.crossZoneVoteCount(), WorldcupVoteEligibilityPolicy.MIN_VOTES,
					WorldcupVoteEligibilityPolicy.MIN_VISITORS,
					WorldcupVoteEligibilityPolicy.MIN_CROSS_ZONE_VOTES))
				.toList()
			: List.of();
		return new WeeklyReportResponse.ListResponse(state, expectedRelease, items,
			currentCollecting);
	}

	public WeeklyReportResponse.Detail detail(long reportId) {
		if (reportId <= 0) {
			throw new IllegalArgumentException("올바른 리포트 번호가 필요합니다.");
		}
		Report report = repository.publishedReport(reportId)
			.orElseThrow(() -> new WeeklyReportNotFoundException(reportId));
		List<Report> reports = repository.publishedReports();
		int position = -1;
		for (int index = 0; index < reports.size(); index++) {
			if (reports.get(index).id() == reportId) {
				position = index;
				break;
			}
		}
		Long next = position > 0 ? reports.get(position - 1).id() : null;
		Long previous = position >= 0 && position + 1 < reports.size()
			? reports.get(position + 1).id() : null;
		List<Zone> currentZones = repository.zones(reportId);
		List<Zone> previousZones = previous == null ? List.of() : repository.zones(previous);
		Map<String, Zone> previousById = new HashMap<>();
		previousZones.forEach(zone -> previousById.put(zone.zoneId(), zone));

		List<ZoneChange> rises = new ArrayList<>();
		List<ZoneChange> falls = new ArrayList<>();
		List<BoundaryZone> boundaries = new ArrayList<>();
		List<CollectingZone> collecting = new ArrayList<>();
		Map<String, CityTotals> cities = new HashMap<>();
		for (Zone current : currentZones) {
			if (current.rankingBasis() == RankingBasis.INITIAL) {
				if (current.validVoteCount() > 0) {
					collecting.add(new CollectingZone(current.zoneId(), current.zoneName(),
						current.cityName(), current.regionName(), current.rankingBasis(),
						current.validVoteCount(), current.participantCount(),
						current.crossZoneVoteCount(),
						WorldcupVoteEligibilityPolicy.MIN_VOTES,
						WorldcupVoteEligibilityPolicy.MIN_VISITORS,
						WorldcupVoteEligibilityPolicy.MIN_CROSS_ZONE_VOTES));
				}
				continue;
			}
			int grade = current.voteTier().intValue();
			if (grade > 1) {
				BigDecimal upperBoundary = BigDecimal.valueOf(grade).subtract(TENTH);
				boundaries.add(new BoundaryZone(current.zoneId(), current.zoneName(),
					current.cityName(), current.voteTier(), upperBoundary,
					current.voteTier().subtract(upperBoundary)));
			}
			Zone old = previousById.get(current.zoneId());
			if (old == null || old.rankingBasis() != RankingBasis.VOTE) {
				continue;
			}
			int comparison = current.voteTier().compareTo(old.voteTier());
			if (comparison != 0) {
				ZoneChange change = new ZoneChange(current.zoneId(), current.zoneName(),
					current.cityName(), current.regionName(), old.voteTier(), current.voteTier(),
					current.voteTier().subtract(old.voteTier()).abs());
				(comparison < 0 ? rises : falls).add(change);
			}
			cities.computeIfAbsent(current.cityId(), ignored ->
				new CityTotals(current.cityId(), current.cityName(), current.regionName()))
				.add(old.voteTier(), current.voteTier());
		}
		rises.sort(CHANGE_ORDER);
		falls.sort(CHANGE_ORDER);
		boundaries.sort(Comparator.comparing(BoundaryZone::distance)
			.thenComparing(BoundaryZone::zoneName).thenComparing(BoundaryZone::zoneId));
		collecting.sort(Comparator.comparingInt(CollectingZone::validVoteCount).reversed()
			.thenComparing(CollectingZone::zoneName).thenComparing(CollectingZone::zoneId));
		List<CityChange> cityChanges = cities.values().stream()
			.map(CityTotals::change).filter(change -> change.movement() != null)
			.sorted(Comparator.comparing(CityChange::averageTierDelta,
				Comparator.reverseOrder()).thenComparing(CityChange::cityName)
				.thenComparing(CityChange::cityId)).limit(5).toList();
		return new WeeklyReportResponse.Detail(report.id(), report.periodStartAt(),
			report.periodEndAt(), report.publishedAt(), report.cutoffCompletedAt(),
			report.participantCount(), report.validVoteCount(),
			report.changedGradeZoneCount(), report.summaryText(), previous, next,
			rises.stream().limit(5).toList(), falls.stream().limit(5).toList(),
			boundaries.stream().limit(5).toList(), collecting.stream().limit(5).toList(),
			cityChanges);
	}

	private static final class CityTotals {
		private final String id;
		private final String name;
		private final String regionName;
		private BigDecimal previous = BigDecimal.ZERO;
		private BigDecimal current = BigDecimal.ZERO;
		private int count;

		private CityTotals(String id, String name, String regionName) {
			this.id = id;
			this.name = name;
			this.regionName = regionName;
		}

		private void add(BigDecimal oldTier, BigDecimal currentTier) {
			previous = previous.add(oldTier);
			current = current.add(currentTier);
			count++;
		}

		private CityChange change() {
			BigDecimal oldAverage = previous.divide(BigDecimal.valueOf(count), 2,
				RoundingMode.HALF_UP);
			BigDecimal newAverage = current.divide(BigDecimal.valueOf(count), 2,
				RoundingMode.HALF_UP);
			int direction = newAverage.compareTo(oldAverage);
			return new CityChange(id, name, regionName, oldAverage, newAverage,
				newAverage.subtract(oldAverage).abs(), direction < 0 ? Movement.UP
				: direction > 0 ? Movement.DOWN : null, count);
		}
	}
}
