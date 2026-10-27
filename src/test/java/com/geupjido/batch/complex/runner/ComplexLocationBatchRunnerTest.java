package com.geupjido.batch.complex.runner;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import com.geupjido.batch.complex.service.ComplexLocationBatchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplexLocationBatchRunnerTest {

	@Test
	void 수동_배치는_설정된_커서와_제한_건수를_전달한다() {
		var environment = new MockEnvironment()
			.withProperty("app.complex-location-import.batch-after-id", "A10000001")
			.withProperty("app.complex-location-import.batch-max-count", "3");
		var batchService = mock(ComplexLocationBatchService.class);
		when(batchService.processBatch("A10000001", 3))
			.thenReturn(new ComplexLocationBatchService.BatchResult(
				"A10000002", List.of(), List.of()
			));

		new ComplexLocationBatchRunner(environment, batchService).run(null);

		verify(batchService).processBatch("A10000001", 3);
	}

	@Test
	void 단건과_배치를_동시에_켜면_처리하지_않는다() {
		var environment = new MockEnvironment()
			.withProperty("app.complex-location-import.enabled", "true");
		var batchService = mock(ComplexLocationBatchService.class);

		assertThatThrownBy(() ->
			new ComplexLocationBatchRunner(environment, batchService).run(null)
		).isInstanceOf(IllegalStateException.class);
		verifyNoInteractions(batchService);
	}

	@Test
	void 기본_설정에서는_배치_러너가_등록되지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(ComplexLocationBatchRunner.class)
			.withPropertyValues("app.complex-location-import.batch-enabled=false")
			.run(context -> assertThat(context)
				.doesNotHaveBean(ComplexLocationBatchRunner.class));
	}
}
