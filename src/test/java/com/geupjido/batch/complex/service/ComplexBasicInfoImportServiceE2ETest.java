package com.geupjido.batch.complex.service;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.geupjido.batch.complex.model.ComplexImportStatus;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_COMPLEX_SAVE_E2E",
	matches = "true"
)
@SpringBootTest
@ActiveProfiles("local")
class ComplexBasicInfoImportServiceE2ETest {

	private static final String COMPLEX_CODE = "A10027875";

	@Autowired
	private ComplexBasicInfoImportService importService;

	@Autowired
	private ComplexRepository complexRepository;

	@Test
	void 실제_API에서_조회한_신규_단지를_저장하고_재실행하면_건너뛴다() {
		assertThat(complexRepository.existsById(COMPLEX_CODE))
			.as("E2E 검증 전에 대상 단지가 없어야 합니다.")
			.isFalse();

		long countBeforeImport = complexRepository.count();

		ComplexImportStatus firstStatus =
			importService.importIfAbsent(COMPLEX_CODE);

		Complex savedComplex = complexRepository
			.findById(COMPLEX_CODE)
			.orElseThrow();

		assertThat(firstStatus)
			.isEqualTo(ComplexImportStatus.CREATED);
		assertThat(savedComplex.getId())
			.isEqualTo(COMPLEX_CODE);
		assertThat(savedComplex.getName())
			.isEqualTo("괴정 경성스마트W아파트");
		assertThat(savedComplex.getAddressJibun())
			.isEqualTo(
				"부산광역시 사하구 괴정동 258 "
					+ "괴정 경성스마트W아파트"
			);
		assertThat(savedComplex.getAddressRoad())
			.isEqualTo("부산광역시 사하구 낙동대로 180");
		assertThat(savedComplex.getHouseholds())
			.isEqualTo(182);
		assertThat(savedComplex.getBuildingCount())
			.isEqualTo(3);
		assertThat(savedComplex.getApprovalDate())
			.isEqualTo(LocalDate.of(2015, 8, 6));
		assertThat(complexRepository.count())
			.isEqualTo(countBeforeImport + 1);

		ComplexImportStatus secondStatus =
			importService.importIfAbsent(COMPLEX_CODE);

		assertThat(secondStatus)
			.isEqualTo(
				ComplexImportStatus.SKIPPED_ALREADY_EXISTS
			);
		assertThat(complexRepository.count())
			.isEqualTo(countBeforeImport + 1);
	}
}
