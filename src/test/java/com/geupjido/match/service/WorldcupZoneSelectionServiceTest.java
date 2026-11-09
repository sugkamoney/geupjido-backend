package com.geupjido.match.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.repository.MatchCandidateRepository;
import com.geupjido.match.repository.WorldcupZoneSelectionRepository;
import com.geupjido.match.repository.WorldcupZoneSelectionRepository.ZoneRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldcupZoneSelectionServiceTest {

	private final List<ZoneRow> rows = List.of(
		new ZoneRow("empty", "서울", "비어 있는 권역", new BigDecimal("1.5"), 0),
		new ZoneRow("ready", "서울", "시작 가능", new BigDecimal("1.5"), 4),
		new ZoneRow("opponent", "서울", "상대 권역", new BigDecimal("1.6"), 4),
		new ZoneRow("sparse", "서울", "대진 부족", new BigDecimal("3.0"), 1)
	);

	private final List<MatchZoneCandidate> candidates = List.of(
		candidate("ready", "1.5", 4),
		candidate("opponent", "1.6", 4),
		candidate("sparse", "3.0", 1)
	);

	private final WorldcupZoneSelectionService service = new WorldcupZoneSelectionService(
		new WorldcupZoneSelectionRepository() {
			@Override public List<ZoneRow> findAll() { return rows; }
			@Override public List<String> findAdjacentZoneIds(String zoneId) {
				return switch (zoneId) {
					case "empty" -> List.of("ready", "sparse");
					case "sparse" -> List.of();
					default -> List.of("empty");
				};
			}
		},
		new MatchCandidateRepository() {
			@Override public List<MatchZoneCandidate> findActiveZones() { return candidates; }
			@Override public List<MatchComplexCandidate> findActiveComplexesByZoneIds(
				Collection<String> zoneIds
			) { return List.of(); }
		},
		new WorldcupMatchAvailability()
	);

	@Test
	void 후보가_없거나_대진이_부족한_권역도_목록에_표시하되_시작_불가로_구분한다() {
		assertThat(service.list()).hasSize(4);
		assertThat(service.list()).extracting("zoneId", "activeComplexCount", "canStart")
			.contains(
				org.assertj.core.groups.Tuple.tuple("empty", 0L, false),
				org.assertj.core.groups.Tuple.tuple("ready", 4L, true),
				org.assertj.core.groups.Tuple.tuple("sparse", 1L, false)
			);
	}

	@Test
	void 시작할_수_없는_권역에는_대진이_가능한_인접_권역만_안내한다() {
		assertThat(service.alternatives("empty"))
			.extracting("zoneId").containsExactly("ready");
		assertThat(service.alternatives("sparse")).isEmpty();
		assertThat(service.alternatives("ready")).isEmpty();
	}

	@Test
	void 존재하지_않는_권역은_오류로_구분한다() {
		assertThatThrownBy(() -> service.alternatives("unknown"))
			.isInstanceOf(WorldcupZoneSelectionNotFoundException.class);
	}

	private MatchZoneCandidate candidate(String id, String tier, long count) {
		return new MatchZoneCandidate(id, "11170", id, new BigDecimal(tier),
			127.0, 37.0, count);
	}
}
