package com.geupjido.match.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.GeneratedMatchGame;
import com.geupjido.match.model.MatchCard;
import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchType;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.model.Matchup;
import com.geupjido.match.repository.MatchCandidateRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchGenerationServiceTest {

	@Test
	void 일반_권역은_다른_권역_6판과_같은_권역_4판을_중복_없이_만든다() {
		MatchGenerationService service = service(7,
			zone("base", "11170", "1.5", 4),
			zone("near", "11170", "1.5", 3),
			zone("middle", "41135", "1.6", 3),
			zone("far", "28185", "1.7", 3));

		GeneratedMatchGame game = service.generateForZone("base");

		assertThat(game.baseZoneId()).isEqualTo("base");
		assertThat(game.tierRangeUsed()).isEqualByComparingTo("0.2");
		assertThat(game.matchups()).hasSize(10);
		assertThat(game.matchups().stream().filter(m -> m.type() == MatchType.WITHIN_ZONE))
			.hasSize(4);
		assertThat(game.matchups().stream().filter(m -> m.type() == MatchType.CROSS_ZONE))
			.hasSize(6);
		assertThat(game.matchups().stream().map(this::pairKey).distinct()).hasSize(10);

		Map<String, Long> opponentZones = game.matchups().stream()
			.filter(m -> m.type() == MatchType.CROSS_ZONE)
			.map(m -> m.left().complexId().startsWith("base-") ? m.right() : m.left())
			.collect(Collectors.groupingBy(MatchCard::zoneName, Collectors.counting()));
		assertThat(opponentZones).containsEntry("near", 2L)
			.containsEntry("middle", 2L).containsEntry("far", 2L);
		assertThat(game.matchups().getFirst().left().representativeSale().contractDate())
			.isEqualTo(LocalDate.of(2026, 9, 1));
	}

	@Test
	void 단지가_하나인_권역은_다른_권역으로_10판을_채운다() {
		MatchGenerationService service = service(8,
			zone("base", "11170", "1.5", 1),
			zone("a", "11170", "1.5", 5),
			zone("b", "41135", "1.6", 5),
			zone("c", "28185", "1.7", 5));

		GeneratedMatchGame game = service.generateForZone("base");

		assertThat(game.matchups()).hasSize(10)
			.allSatisfy(m -> assertThat(m.type()).isEqualTo(MatchType.CROSS_ZONE));
		assertThat(game.matchups().stream().map(this::pairKey).distinct()).hasSize(10);
		assertThat(game.matchups()).allSatisfy(m -> assertThat(
			List.of(m.left().complexId(), m.right().complexId())
		).contains("base-1"));
	}

	@Test
	void 실제_단지_쌍이_부족할_때만_급지_범위를_한_단계_넓힌다() {
		MatchGenerationService service = service(9,
			zone("base", "11170", "1.5", 1),
			zone("same", "11170", "1.7", 5),
			zone("extended", "41135", "1.8", 5));

		GeneratedMatchGame game = service.generateForZone("base");

		assertThat(game.tierRangeUsed()).isEqualByComparingTo("0.3");
		assertThat(game.matchups()).hasSize(10);
		assertThat(game.matchups().stream().map(this::pairKey).distinct()).hasSize(10);
	}

	@Test
	void 확장해도_10판이_부족하면_게임을_시작하지_않는다() {
		MatchGenerationService service = service(10,
			zone("base", "11170", "1.5", 1),
			zone("other", "41135", "1.8", 9));

		assertThatThrownBy(() -> service.generateForZone("base"))
			.isInstanceOf(MatchUnavailableException.class);
		assertThatThrownBy(() -> service.generateForZone("missing"))
			.isInstanceOf(MatchUnavailableException.class);
	}

	@Test
	void 배너는_단지가_적은_권역을_제외하고_서울_60퍼센트로_추첨한다() {
		MatchGenerationService service = service(11,
			zone("seoul", "11170", "1.5", 4),
			zone("sparse", "11170", "1.5", 1),
			zone("gyeonggi", "41135", "1.5", 4),
			zone("incheon", "28185", "1.5", 4));

		List<String> baseZones = IntStream.range(0, 500)
			.mapToObj(index -> service.generateFromBanner().baseZoneId())
			.toList();
		long seoulGames = baseZones.stream().filter("seoul"::equals).count();

		assertThat(baseZones).doesNotContain("sparse");
		assertThat(seoulGames).isBetween(250L, 350L);
	}

	private MatchGenerationService service(long seed, TestZone... zones) {
		return new MatchGenerationService(new FakeRepository(List.of(zones)), new Random(seed));
	}

	private TestZone zone(String id, String cityId, String tier, int complexCount) {
		MatchZoneCandidate zone = new MatchZoneCandidate(
			id, cityId, id, new BigDecimal(tier), 127.0, 37.5, complexCount
		);
		List<MatchComplexCandidate> complexes = IntStream.rangeClosed(1, complexCount)
			.mapToObj(index -> new MatchComplexCandidate(
				id + "-" + index, id, id + " 아파트 " + index,
				"시·구", id, new BigDecimal(tier), 500,
				LocalDate.of(2020, 1, 1), new BigDecimal("59.8"),
				100_000, LocalDate.of(2026, 9, 1)
			))
			.toList();
		return new TestZone(zone, complexes);
	}

	private String pairKey(Matchup matchup) {
		return Set.of(matchup.left().complexId(), matchup.right().complexId()).stream()
			.sorted().collect(Collectors.joining("/"));
	}

	private record TestZone(MatchZoneCandidate zone, List<MatchComplexCandidate> complexes) {
	}

	private static final class FakeRepository implements MatchCandidateRepository {
		private final List<MatchZoneCandidate> zones;
		private final Map<String, List<MatchComplexCandidate>> complexesByZone;

		private FakeRepository(List<TestZone> testZones) {
			zones = testZones.stream().map(TestZone::zone).toList();
			complexesByZone = testZones.stream().collect(Collectors.toMap(
				testZone -> testZone.zone().zoneId(), TestZone::complexes
			));
		}

		@Override
		public List<MatchZoneCandidate> findActiveZones() {
			return zones;
		}

		@Override
		public List<MatchComplexCandidate> findActiveComplexesByZoneIds(Collection<String> zoneIds) {
			List<MatchComplexCandidate> result = new ArrayList<>();
			for (String zoneId : zoneIds) {
				result.addAll(complexesByZone.getOrDefault(zoneId, List.of()));
			}
			return result;
		}
	}
}
