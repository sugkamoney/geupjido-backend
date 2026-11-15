package com.geupjido.ranking.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.ranking.model.RankingSource;
import com.geupjido.ranking.model.RankingView;
import com.geupjido.ranking.model.RankingView.Movement;
import com.geupjido.ranking.model.RankingView.Region;
import com.geupjido.ranking.model.RankingView.Sort;
import com.geupjido.ranking.model.RankingView.Tab;
import com.geupjido.ranking.repository.RankingRepository;
import com.geupjido.zone.domain.RankingBasis;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RankingQueryService {
	private static final BigDecimal SCORE_BASE = new BigDecimal("100");
	private static final BigDecimal SCORE_SCALE = BigDecimal.TEN;
	private static final Comparator<Candidate> RANK_ORDER = Comparator
		.comparing(Candidate::tier, Comparator.nullsLast(Comparator.naturalOrder()))
		.thenComparing(Candidate::score, Comparator.nullsLast(Comparator.reverseOrder()))
		.thenComparing((Candidate item) -> item.source().validVoteCount(),
			Comparator.reverseOrder())
		.thenComparing(item -> item.source().name())
		.thenComparing(item -> item.source().id());

	private final RankingRepository repository;

	public RankingQueryService(RankingRepository repository) {
		this.repository = repository;
	}

	public RankingView.Response zones(Region region, Tab tab, Sort sort) {
		if (region == null || tab == null || sort == null) {
			throw new IllegalArgumentException("지역·급지 탭·정렬을 확인해 주세요.");
		}
		if (tab == Tab.COLLECTING && sort != Sort.CURRENT) {
			throw new IllegalArgumentException("모으는 중 탭은 현재 정렬만 사용할 수 있습니다.");
		}

		RankingSource.Publication publication = repository.publication();
		List<Candidate> candidates = repository.zones(region).stream()
			.map(source -> candidate(source, publication)).sorted(RANK_ORDER).toList();
		long rankedCount = candidates.stream().filter(item -> item.tier() != null).count();
		long collectingCount = candidates.stream()
			.filter(item -> item.source().rankingBasis() == RankingBasis.INITIAL).count();
		long[] counts = new long[5];
		List<RankingView.Zone> ranked = new ArrayList<>(candidates.size());
		long rank = 0;
		for (Candidate item : candidates) {
			if (item.tier() != null) {
				counts[item.tier().intValue() - 1]++;
				rank++;
			}
			RankingSource.Zone source = item.source();
			ranked.add(new RankingView.Zone(source.id(), source.name(),
				source.cityName(), source.regionName(), source.rankingBasis(),
				item.tier(), item.tier() == null ? null : rank, rankedCount,
				item.previousTier(), item.tierDelta(), item.movement(),
				source.validVoteCount(), source.participantCount(),
				source.crossZoneVoteCount()));
		}

		List<RankingView.Zone> visible = ranked.stream()
			.filter(item -> inTab(item, tab))
			.filter(item -> matchesSort(item, sort))
			.sorted(listOrder(tab, sort))
			.toList();
		List<RankingView.GradeCount> gradeCounts = new ArrayList<>(5);
		for (int grade = 1; grade <= 5; grade++) {
			gradeCounts.add(new RankingView.GradeCount(grade, counts[grade - 1]));
		}
		return new RankingView.Response(region, tab, sort, candidates.size(),
			rankedCount, gradeCounts, collectingCount,
			publication.lastCompletedAt(), publication.previousCompletedAt(), visible);
	}

	private static Candidate candidate(RankingSource.Zone source,
		RankingSource.Publication publication) {
		BigDecimal tier = source.rankingBasis() == RankingBasis.VOTE
			? source.voteTier() : source.initialTier();
		BigDecimal score = source.rankingBasis() == RankingBasis.VOTE
			? source.voteScore() : source.initialTier() == null ? null
				: SCORE_BASE.subtract(source.initialTier().multiply(SCORE_SCALE));
		if (source.rankingBasis() == RankingBasis.VOTE && (tier == null || score == null)) {
			throw new IllegalStateException("민심 급지의 점수 또는 급지가 없습니다: " + source.id());
		}
		BigDecimal previous = publication.lastCompletedAt() == null
			? null : source.previousPublishedTier();
		if (previous == null || tier == null) {
			return new Candidate(source, tier, score, null, null, Movement.NONE);
		}
		int comparison = previous.compareTo(tier);
		Movement movement = comparison > 0 ? Movement.UP
			: comparison < 0 ? Movement.DOWN : Movement.SAME;
		return new Candidate(source, tier, score, previous,
			previous.subtract(tier).abs(), movement);
	}

	private static boolean inTab(RankingView.Zone item, Tab tab) {
		if (tab == Tab.COLLECTING) {
			return item.rankingBasis() == RankingBasis.INITIAL;
		}
		int grade = switch (tab) {
			case GRADE_1 -> 1;
			case GRADE_2 -> 2;
			case GRADE_3 -> 3;
			case GRADE_4 -> 4;
			case GRADE_5 -> 5;
			case COLLECTING -> throw new IllegalStateException("이미 처리한 탭입니다.");
		};
		return item.tier() != null
			&& item.tier().intValue() == grade;
	}

	private static boolean matchesSort(RankingView.Zone item, Sort sort) {
		return switch (sort) {
			case CURRENT -> true;
			case RISEN -> item.movement() == Movement.UP;
			case FALLEN -> item.movement() == Movement.DOWN;
		};
	}

	private static Comparator<RankingView.Zone> listOrder(Tab tab, Sort sort) {
		Comparator<RankingView.Zone> byRank = Comparator.comparing(
			RankingView.Zone::rank, Comparator.nullsLast(Comparator.naturalOrder()));
		if (tab == Tab.COLLECTING) {
			return Comparator.comparingInt(RankingView.Zone::validVoteCount).reversed()
				.thenComparing(byRank).thenComparing(RankingView.Zone::zoneId);
		}
		if (sort == Sort.CURRENT) {
			return byRank;
		}
		return Comparator.comparing(RankingView.Zone::tierDelta,
			Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byRank);
	}

	private record Candidate(RankingSource.Zone source, BigDecimal tier, BigDecimal score,
		BigDecimal previousTier, BigDecimal tierDelta, Movement movement) {
	}
}
