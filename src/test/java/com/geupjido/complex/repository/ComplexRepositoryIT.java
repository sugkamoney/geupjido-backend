package com.geupjido.complex.repository;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.complex.domain.Complex;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_POSTGIS_IT",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ComplexRepositoryIT {

	@Autowired
	private ComplexRepository complexRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 미처리_단지_ID를_기준으로_건수를_제한해_이어_조회한다() {
		Complex first = new Complex(
			"Z900000001", "첫 번째 단지", "테스트 주소 1", null,
			"1168010100", 100, null, null
		);
		Complex second = new Complex(
			"Z900000002", "두 번째 단지", "테스트 주소 2", null,
			"1168010100", 100, null, null
		);
		complexRepository.saveAllAndFlush(List.of(first, second));
		entityManager.clear();

		List<String> firstPage =
			complexRepository.findIdsNeedingLocationOrZone(
				"Z900000000", PageRequest.of(0, 1)
			);
		List<String> secondPage =
			complexRepository.findIdsNeedingLocationOrZone(
				firstPage.getFirst(), PageRequest.of(0, 1)
			);

		assertThat(firstPage).containsExactly("Z900000001");
		assertThat(secondPage).containsExactly("Z900000002");
	}

	@Test
	void 단지를_저장하고_조회한다() {
		Complex complex = new Complex(
			"T00000001",
			"통합테스트아파트",
			"서울특별시 강남구 테스트동 1",
			"서울특별시 강남구 테스트로 1",
			"1168010100",
			182,
			3,
			LocalDate.of(2015, 8, 6)
		);

		complexRepository.saveAndFlush(complex);
		entityManager.clear();

		Complex found = complexRepository.findById("T00000001")
			.orElseThrow();

		assertThat(found.getId()).isEqualTo("T00000001");
		assertThat(found.getName()).isEqualTo("통합테스트아파트");
		assertThat(found.getAddressJibun())
			.isEqualTo("서울특별시 강남구 테스트동 1");
		assertThat(found.getAddressRoad())
			.isEqualTo("서울특별시 강남구 테스트로 1");
		assertThat(found.getLegalDongCode())
			.isEqualTo("1168010100");
		assertThat(found.getHouseholds()).isEqualTo(182);
		assertThat(found.getBuildingCount()).isEqualTo(3);
		assertThat(found.getApprovalDate())
			.isEqualTo(LocalDate.of(2015, 8, 6));
		assertThat(found.getZone()).isNull();
		assertThat(found.isActive()).isFalse();
	}
}
