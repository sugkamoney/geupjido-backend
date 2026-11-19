package com.geupjido.report.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.match.service.WorldcupVoteAggregationInputService;
import com.geupjido.match.service.WorldcupVoteEligibilityPolicy.ZoneCount;
import com.geupjido.match.service.WorldcupVoteRatingService;
import com.geupjido.report.model.WeeklyReportPeriod;
import com.geupjido.report.model.WeeklyReportSource.Zone;
import com.geupjido.report.repository.WeeklyReportRepository;
import com.geupjido.zone.domain.RankingBasis;

@Service
public class WeeklyReportPublicationService {
	private static final ZoneCount EMPTY_COUNT = new ZoneCount(0, 0, 0);

	private final WeeklyReportRepository repository;
	private final WorldcupVoteRatingService ratingService;
	private final WorldcupVoteAggregationInputService voteInputService;
	private final WeeklyReportSummaryBuilder summaryBuilder;
	private final Clock clock;

	public WeeklyReportPublicationService(WeeklyReportRepository repository,
		WorldcupVoteRatingService ratingService,
		WorldcupVoteAggregationInputService voteInputService,
		WeeklyReportSummaryBuilder summaryBuilder, Clock clock) {
		this.repository = repository;
		this.ratingService = ratingService;
		this.voteInputService = voteInputService;
		this.summaryBuilder = summaryBuilder;
		this.clock = clock;
	}

	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public boolean prepareLatestClosed() {
		Instant now = clock.instant();
		WeeklyReportPeriod period = WeeklyReportPeriod.latestClosedAt(now);
		// 기존 3시간 급지 발행과 주간 초안의 쓰기 순서를 한 행으로 직렬화한다.
		repository.lockPublication();
		if (repository.existsForPeriod(period.endAt())) {
			return false;
		}

		var calculation = ratingService.calculateBefore(period.endAt());
		var input = calculation.input();
		Map<String, ZoneCount> counts = input.zoneCounts();
		List<Zone> zones = new ArrayList<>();
		boolean hasVoteTier = false;
		for (var identity : repository.currentZones()) {
			ZoneCount count = counts.getOrDefault(identity.zoneId(), EMPTY_COUNT);
			boolean voteBased = count.readyForVoteRanking();
			BigDecimal tier = null;
			if (voteBased) {
				var score = input.ratings().zoneScores().get(identity.zoneId());
				if (score == null) {
					throw new IllegalStateException("마감 급지 계산에 권역이 없습니다: " + identity.zoneId());
				}
				tier = score.tier();
				hasVoteTier = true;
			}
			zones.add(new Zone(identity.zoneId(), identity.zoneName(),
				identity.cityId(), identity.cityName(), identity.regionCode(),
				identity.regionName(), voteBased ? RankingBasis.VOTE : RankingBasis.INITIAL,
				identity.initialTier(), tier, count.validVotes(), count.distinctVisitors(), count.crossZoneVotes()));
		}
		if (!hasVoteTier) {
			// 마감 집계가 정상 완료됐으므로 이전 시도의 일시적 실패 상태를 해제한다.
			repository.clearFailedAttempt(period.endAt());
			return false;
		}

		Set<UUID> periodParticipants = new HashSet<>();
		AtomicLong periodVotes = new AtomicLong();
		voteInputService.forEachValidVoteBefore(period.endAt(), vote -> {
			if (!vote.selectedAt().isBefore(period.startAt())) {
				periodVotes.incrementAndGet();
				periodParticipants.add(vote.anonymousId());
			}
		});
		var previous = repository.previousPublished(period.endAt());
		List<Zone> previousZones = previous.map(report -> repository.zones(report.id()))
			.orElseGet(List::of);
		Integer changedGrades = previous.isPresent()
			? changedGradeCount(previousZones, zones) : null;
		String summary = summaryBuilder.build(period.endAt(), previous.isPresent(), previousZones, zones);
		repository.saveDraft(period, clock.instant(), periodVotes.get(),
			periodParticipants.size(), changedGrades, summary, zones);
		return true;
	}

	@Transactional
	public boolean publishLatestReady() {
		Instant now = clock.instant();
		WeeklyReportPeriod period = WeeklyReportPeriod.latestClosedAt(now);
		if (now.isBefore(period.releaseAt())) {
			return false;
		}
		if (!repository.existsForPeriod(period.endAt())) {
			return false;
		}
		return repository.publish(period.endAt(), clock.instant());
	}

	private static int changedGradeCount(List<Zone> previous, List<Zone> current) {
		Map<String, BigDecimal> previousTiers = new HashMap<>();
		for (Zone zone : previous) {
			if (zone.rankingBasis() == RankingBasis.VOTE) {
				previousTiers.put(zone.zoneId(), zone.voteTier());
			}
		}
		int changed = 0;
		for (Zone zone : current) {
			if (zone.rankingBasis() != RankingBasis.VOTE) {
				continue;
			}
			BigDecimal oldTier = previousTiers.get(zone.zoneId());
			if (oldTier != null && oldTier.intValue() != zone.voteTier().intValue()) {
				changed++;
			}
		}
		return changed;
	}
}
