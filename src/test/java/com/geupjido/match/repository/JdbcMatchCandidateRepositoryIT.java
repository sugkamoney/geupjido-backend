package com.geupjido.match.repository;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.geupjido.match.model.MatchComplexCandidate;
import com.geupjido.match.model.MatchZoneCandidate;
import com.geupjido.match.service.MatchGenerationService;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_POSTGIS_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("local")
class JdbcMatchCandidateRepositoryIT {

	@Autowired
	private MatchCandidateRepository repository;

	@Autowired
	private MatchGenerationService matchGenerationService;

	@Test
	void 활성_권역의_단지_수와_카드_후보를_실제_DB에서_읽는다() {
		List<MatchZoneCandidate> zones = repository.findActiveZones();
		assertThat(zones).isNotEmpty();

		MatchZoneCandidate zone = zones.getFirst();
		List<MatchComplexCandidate> complexes =
			repository.findActiveComplexesByZoneIds(List.of(zone.zoneId()));

		assertThat(complexes).hasSize((int) zone.activeComplexCount());
		assertThat(complexes).allSatisfy(complex -> {
			assertThat(complex.zoneId()).isEqualTo(zone.zoneId());
			assertThat(complex.cityName()).isNotBlank();
			assertThat(complex.zoneTier()).isEqualByComparingTo(zone.tier());
			assertThat(complex.priceTenThousandWon()).isPositive();
			assertThat(complex.representativeArea()).isPositive();
			assertThat(complex.approvalDate()).isNotNull();
			assertThat(complex.lastDealDate()).isNotNull();
		});
		assertThat(repository.findActiveComplexesByZoneIds(List.of())).isEmpty();
	}

	@Test
	void 실제_활성_권역으로_10판을_생성한다() {
		List<MatchZoneCandidate> zones = repository.findActiveZones();
		MatchZoneCandidate normal = zones.stream()
			.filter(zone -> zone.activeComplexCount() >= 4)
			.findFirst().orElseThrow();
		MatchZoneCandidate sparse = zones.stream()
			.filter(zone -> zone.activeComplexCount() <= 3)
			.findFirst().orElseThrow();

		assertThat(matchGenerationService.generateForZone(normal.zoneId()).matchups())
			.hasSize(10);
		assertThat(matchGenerationService.generateForZone(sparse.zoneId()).matchups())
			.hasSize(10);
		assertThat(matchGenerationService.generateFromBanner().matchups())
			.hasSize(10);
	}

	@Test
	void 모든_활성_권역에서_10판을_생성한다() {
		for (MatchZoneCandidate zone : repository.findActiveZones()) {
			assertThat(matchGenerationService.generateForZone(zone.zoneId()).matchups())
				.as("권역 %s", zone.zoneId())
				.hasSize(10);
		}
	}
}
