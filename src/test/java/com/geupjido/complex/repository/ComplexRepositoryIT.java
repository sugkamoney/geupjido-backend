package com.geupjido.complex.repository;

import java.time.LocalDate;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
