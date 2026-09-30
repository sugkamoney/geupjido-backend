package com.geupjido.batch.complex.runner;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.complex.config.ComplexBulkImportProperties;
import com.geupjido.batch.complex.service.ComplexBulkImportService;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.complex.repository.ComplexRepository;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(
	named = "RUN_COMPLEX_BULK_IMPORT_E2E",
	matches = "true"
)
@SpringBootTest(
	properties = {
		"app.external.complex-list.enabled=true",
		"app.external.complex-basic-info.enabled=true",
		"app.complex-import.enabled=false"
	}
)
@ActiveProfiles("local")
@Transactional
class ComplexBulkImportRunnerE2ETest {

	private static final String LEGAL_DONG_CODE =
		"1117013100";

	private static final Path MAPPING_PATH =
		Path.of(
			"src/test/resources/batch/complex/"
				+ "complex-import-e2e-mapping.json"
		);

	@Autowired
	private LocationMappingReader mappingReader;

	@Autowired
	private ComplexBulkImportService importService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ComplexRepository complexRepository;

	@Test
	void 소범위_매핑으로_단지를_저장하고_재실행하면_중복_저장하지_않는다() {
		jdbcTemplate.update(
			"""
			DELETE FROM complex
			WHERE legal_dong_code = ?
			""",
			LEGAL_DONG_CODE
		);

		ComplexBulkImportProperties properties =
			new ComplexBulkImportProperties(
				true,
				MAPPING_PATH
			);

		ComplexBulkImportRunner runner =
			new ComplexBulkImportRunner(
				properties,
				mappingReader,
				importService
			);

		runner.run(null);
		complexRepository.flush();

		Integer countAfterFirstRun =
			countImportedComplexes();

		assertThat(countAfterFirstRun)
			.isPositive();

		runner.run(null);
		complexRepository.flush();

		Integer countAfterSecondRun =
			countImportedComplexes();

		assertThat(countAfterSecondRun)
			.isEqualTo(countAfterFirstRun);
	}

	private Integer countImportedComplexes() {
		return jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM complex
			WHERE legal_dong_code = ?
			""",
			Integer.class,
			LEGAL_DONG_CODE
		);
	}
}
