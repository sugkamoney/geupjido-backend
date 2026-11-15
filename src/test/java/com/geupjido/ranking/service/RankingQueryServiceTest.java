package com.geupjido.ranking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.ranking.model.RankingSource;
import com.geupjido.ranking.model.RankingView;
import com.geupjido.ranking.model.RankingView.Movement;
import com.geupjido.ranking.model.RankingView.Region;
import com.geupjido.ranking.model.RankingView.Sort;
import com.geupjido.ranking.model.RankingView.Tab;
import com.geupjido.ranking.repository.RankingRepository;
import com.geupjido.zone.domain.RankingBasis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingQueryServiceTest {
	private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");
	private static final Instant BEFORE = Instant.parse("2026-11-15T06:00:00Z");

	private final RankingSource.Zone seoulInitial = zone("seoul-initial", "서울초기",
		"서울", "2.4", null, null, RankingBasis.INITIAL, "2.4", 3);
	private final RankingSource.Zone seoulVote = zone("seoul-vote", "서울민심",
		"서울", "2.7", "2.4", "76.50", RankingBasis.VOTE, "2.7", 80);
	private final RankingSource.Zone gyeonggiInitial = zone("gyeonggi-initial", "경기초기",
		"경기", "3.1", null, null, RankingBasis.INITIAL, "3.1", 0);
	private final RankingSource.Zone incheonVote = zone("incheon-vote", "인천민심",
		"인천", "1.7", "1.8", "82.10", RankingBasis.VOTE, "1.7", 55);
	private final RankingSource.Zone unranked = zone("gyeonggi-unranked", "급지없음",
		"경기", null, null, null, RankingBasis.INITIAL, null, 0);

	@Test
	void 현재_급지로_먼저_전체와_지역_순위를_매기고_탭은_순위를_바꾸지_않는다() {
		RankingQueryService service = service(NOW);
		RankingView.Response all = service.zones(Region.ALL, Tab.GRADE_2, Sort.CURRENT);
		assertThat(all.totalZoneCount()).isEqualTo(5);
		assertThat(all.rankedZoneCount()).isEqualTo(4);
		assertThat(all.collectingCount()).isEqualTo(3);
		assertThat(all.gradeCounts().stream().map(RankingView.GradeCount::count))
			.containsExactly(1L, 2L, 1L, 0L, 0L);
		assertThat(all.zones()).extracting(RankingView.Zone::zoneId)
			.containsExactly("seoul-vote", "seoul-initial");
		assertThat(all.zones()).extracting(RankingView.Zone::rank)
			.containsExactly(2L, 3L);
		assertThat(all.zones()).allSatisfy(zone ->
			assertThat(zone.rankTotal()).isEqualTo(4));

		RankingView.Response seoul = service.zones(Region.SEOUL, Tab.GRADE_2, Sort.CURRENT);
		assertThat(seoul.zones()).extracting(RankingView.Zone::rank)
			.containsExactly(1L, 2L);
		assertThat(seoul.zones()).allSatisfy(zone ->
			assertThat(zone.rankTotal()).isEqualTo(2));

		RankingView.Response other = service.zones(Region.GYEONGGI_INCHEON,
			Tab.GRADE_1, Sort.CURRENT);
		assertThat(other.totalZoneCount()).isEqualTo(3);
		assertThat(other.zones()).extracting(RankingView.Zone::zoneId)
			.containsExactly("incheon-vote");
		assertThat(other.zones().getFirst().rank()).isEqualTo(1);
		assertThat(other.zones().getFirst().rankTotal()).isEqualTo(2);
	}

	@Test
	void 모으는_중은_급지_탭과_겹치고_유효표가_많은_순으로_보인다() {
		RankingQueryService service = service(NOW);
		RankingView.Response collecting = service.zones(Region.ALL,
			Tab.COLLECTING, Sort.CURRENT);
		assertThat(collecting.zones()).extracting(RankingView.Zone::zoneId)
			.containsExactly("seoul-initial", "gyeonggi-initial", "gyeonggi-unranked");
		assertThat(collecting.zones().getFirst().rank()).isEqualTo(3);
		assertThat(collecting.zones().getLast().rank()).isNull();
		assertThatThrownBy(() -> service.zones(Region.ALL, Tab.COLLECTING, Sort.RISEN))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 첫_민심_반영은_초기_급지와_비교하고_상승과_하락은_급지_변화로_고른다() {
		RankingQueryService service = service(NOW);
		RankingView.Zone up = service.zones(Region.ALL, Tab.GRADE_2, Sort.RISEN)
			.zones().getFirst();
		assertThat(up.zoneId()).isEqualTo("seoul-vote");
		assertThat(up.previousTier()).isEqualByComparingTo("2.7");
		assertThat(up.tierDelta()).isEqualByComparingTo("0.3");
		assertThat(up.movement()).isEqualTo(Movement.UP);

		RankingView.Zone down = service.zones(Region.ALL, Tab.GRADE_1, Sort.FALLEN)
			.zones().getFirst();
		assertThat(down.zoneId()).isEqualTo("incheon-vote");
		assertThat(down.tierDelta()).isEqualByComparingTo("0.1");
		assertThat(down.movement()).isEqualTo(Movement.DOWN);
		assertThat(service.zones(Region.ALL, Tab.GRADE_2, Sort.FALLEN).zones())
			.isEmpty();
	}

	@Test
	void 첫_발행_전에도_초기_순위가_있고_변동만_없다() {
		RankingQueryService service = service(null);
		RankingView.Response response = service.zones(Region.SEOUL,
			Tab.GRADE_2, Sort.CURRENT);
		assertThat(response.lastCompletedAt()).isNull();
		assertThat(response.zones()).hasSize(2);
		assertThat(response.zones()).allSatisfy(zone -> {
			assertThat(zone.movement()).isEqualTo(Movement.NONE);
			assertThat(zone.previousTier()).isNull();
		});
	}

	private RankingQueryService service(Instant lastCompletedAt) {
		RankingRepository repository = new RankingRepository() {
			@Override
			public RankingSource.Publication publication() {
				return new RankingSource.Publication(lastCompletedAt,
					lastCompletedAt == null ? null : BEFORE);
			}

			@Override
			public List<RankingSource.Zone> zones(Region region) {
				return switch (region) {
					case ALL -> List.of(seoulInitial, seoulVote, gyeonggiInitial,
						incheonVote, unranked);
					case SEOUL -> List.of(seoulInitial, seoulVote);
					case GYEONGGI_INCHEON -> List.of(gyeonggiInitial, incheonVote, unranked);
				};
			}
		};
		return new RankingQueryService(repository);
	}

	private static RankingSource.Zone zone(String id, String name, String regionName,
		String initialTier, String voteTier, String voteScore, RankingBasis basis,
		String previousTier, int validVotes) {
		return new RankingSource.Zone(id, name, "시·구", regionName,
			decimal(initialTier), decimal(voteTier), decimal(voteScore), basis,
			decimal(previousTier), validVotes, validVotes == 0 ? 0 : 10,
			validVotes == 0 ? 0 : 20);
	}

	private static BigDecimal decimal(String value) {
		return value == null ? null : new BigDecimal(value);
	}
}
