package com.geupjido.batch.complex.runner;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportResult;
import com.geupjido.batch.complex.service.ComplexImportJobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplexBulkImportRunnerTest {

	@Test
	void 수동_실행은_공통_작업_서비스를_호출한다() {
		ComplexImportJobService jobService = mock(ComplexImportJobService.class);
		when(jobService.importComplexes()).thenReturn(
			new ComplexBatchImportResult(List.of(
				new ComplexLegalDongImportResult(
					"1117013100",
					new ComplexBulkImportResult(1, 1, 0),
					null
				)
			))
		);

		new ComplexBulkImportRunner(jobService).run(null);

		verify(jobService).importComplexes();
	}

	@Test
	void 수동_적재가_비활성화되면_Runner를_등록하지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(ComplexBulkImportRunner.class)
			.withPropertyValues("app.complex-import.enabled=false")
			.run(context -> assertThat(context)
				.doesNotHaveBean(ComplexBulkImportRunner.class));
	}
}
