package com.geupjido.batch.complex.runner;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.geupjido.batch.complex.config.ComplexBulkImportProperties;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.service.ComplexBulkImportService;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.model.LocationMapping.ZoneDefinition;
import com.geupjido.batch.location.reader.LocationMappingReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexBulkImportRunnerTest {

	@TempDir
	Path tempDir;

	@Mock
	private LocationMappingReader mappingReader;

	@Mock
	private ComplexBulkImportService importService;

	@Test
	void 매핑_파일의_법정동_코드를_순서대로_전달한다()
		throws IOException {

		Path mappingPath =
			tempDir.resolve("location-mapping.json");
		Files.writeString(mappingPath, "{}");

		ComplexBulkImportProperties properties =
			new ComplexBulkImportProperties(
				true,
				mappingPath
			);

		ComplexBulkImportRunner runner =
			new ComplexBulkImportRunner(
				properties,
				mappingReader,
				importService
			);

		LocationMapping mapping =
			new LocationMapping(
				List.of(),
				List.of(),
				List.of(
					new ZoneDefinition(
						"yongsan-hannam",
						"11170",
						"한남",
						List.of("1117013100"),
						new BigDecimal("1.5")
					),
					new ZoneDefinition(
						"seongdong-seongsu",
						"11200",
						"성수",
						List.of(
							"1120011400",
							"1120011500"
						),
						new BigDecimal("1.8")
					)
				)
			);

		List<String> expectedLegalDongCodes =
			List.of(
				"1117013100",
				"1120011400",
				"1120011500"
			);

		when(mappingReader.read(mappingPath))
			.thenReturn(mapping);

		when(
			importService.importByLegalDongCodes(
				expectedLegalDongCodes
			)
		).thenReturn(
			new ComplexBulkImportResult(
				3,
				2,
				1
			)
		);

		runner.run(null);

		InOrder inOrder = inOrder(
			mappingReader,
			importService
		);
		inOrder.verify(mappingReader).read(mappingPath);
		inOrder.verify(importService)
			.importByLegalDongCodes(
				expectedLegalDongCodes
			);
	}

	@Test
	void 매핑_파일_경로가_없으면_실행하지_않는다() {
		ComplexBulkImportProperties properties =
			new ComplexBulkImportProperties(
				true,
				null
			);

		ComplexBulkImportRunner runner =
			new ComplexBulkImportRunner(
				properties,
				mappingReader,
				importService
			);

		assertThatThrownBy(
			() -> runner.run(null)
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
				"공동주택 초기 적재 매핑 파일 경로가 설정되지 않았습니다"
			);

		verifyNoInteractions(
			mappingReader,
			importService
		);
	}

	@Test
	void 매핑_파일이_존재하지_않으면_실행하지_않는다() {
		Path missingMappingPath =
			tempDir.resolve("missing-location-mapping.json");

		ComplexBulkImportProperties properties =
			new ComplexBulkImportProperties(
				true,
				missingMappingPath
			);

		ComplexBulkImportRunner runner =
			new ComplexBulkImportRunner(
				properties,
				mappingReader,
				importService
			);

		assertThatThrownBy(
			() -> runner.run(null)
		)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(
				"공동주택 초기 적재 매핑 파일이 존재하지 않습니다"
			)
			.hasMessageContaining(
				missingMappingPath.toString()
			);

		verifyNoInteractions(
			mappingReader,
			importService
		);
	}

	@Test
	void 초기_적재가_비활성화되면_Runner를_등록하지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(
				ComplexBulkImportRunner.class
			)
			.withPropertyValues(
				"app.complex-import.enabled=false"
			)
			.run(
				context -> assertThat(context)
					.doesNotHaveBean(
						ComplexBulkImportRunner.class
					)
			);
	}
}
