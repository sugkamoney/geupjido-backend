package com.geupjido.batch.complex.runner;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import com.geupjido.batch.complex.config.ComplexMonthlySchedulingConfig;
import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.service.ComplexImportJobService;
import com.geupjido.batch.complex.service.ComplexLocationJobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplexMonthlyImportSchedulerTest {

	private final ApplicationContextRunner contextRunner =
		new ApplicationContextRunner()
			.withUserConfiguration(
				ComplexMonthlySchedulingConfig.class,
				ComplexMonthlyImportScheduler.class
			)
			.withBean(
				ComplexImportJobService.class,
				() -> mock(ComplexImportJobService.class)
			)
			.withPropertyValues(
				"app.external.complex-list.enabled=true",
				"app.external.complex-basic-info.enabled=true"
			);

	@Test
	void 기본값에서는_스케줄러를_등록하지_않는다() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ComplexMonthlyImportScheduler.class);
			assertThat(context).doesNotHaveBean(
				"org.springframework.context.annotation.internalScheduledAnnotationProcessor"
			);
		});
	}

	@Test
	void 활성화하면_월간_스케줄러가_공통_작업을_호출한다() {
		contextRunner
			.withPropertyValues("app.complex-import.schedule-enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(ComplexMonthlyImportScheduler.class);
				assertThat(context).hasBean(
					"org.springframework.context.annotation.internalScheduledAnnotationProcessor"
				);

				ComplexImportJobService jobService =
					context.getBean(ComplexImportJobService.class);
				when(jobService.importAllComplexes())
					.thenReturn(new ComplexBatchImportResult(List.of()));

				context.getBean(ComplexMonthlyImportScheduler.class)
					.importMonthly();

				verify(jobService).importAllComplexes();
			});
	}

	@Test
	void 매월_첫날_새벽_두시에_한국_시간으로_실행한다() throws Exception {
		Scheduled schedule = ComplexMonthlyImportScheduler.class
			.getMethod("importMonthly")
			.getAnnotation(Scheduled.class);

		assertThat(schedule.cron()).isEqualTo(
			"${app.complex-import.schedule-cron:0 0 2 1 * *}"
		);
		assertThat(schedule.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void 단지_수집이_끝난_뒤_좌표_작업을_실행한다() {
		contextRunner
			.withBean(
				ComplexLocationJobService.class,
				() -> mock(ComplexLocationJobService.class)
			)
			.withPropertyValues(
				"app.complex-import.schedule-enabled=true",
				"app.external.geocoding.enabled=true",
				"app.complex-location-import.schedule-enabled=true"
			)
			.run(context -> {
				ComplexImportJobService importJob =
					context.getBean(ComplexImportJobService.class);
				ComplexLocationJobService locationJob =
					context.getBean(ComplexLocationJobService.class);
				when(importJob.importAllComplexes())
					.thenReturn(new ComplexBatchImportResult(List.of()));
				when(locationJob.processPending()).thenReturn(
					new ComplexLocationJobService.JobResult(1, 1, 0, 0, 0, "A1")
				);

				context.getBean(ComplexMonthlyImportScheduler.class)
					.importMonthly();

				var order = inOrder(importJob, locationJob);
				order.verify(importJob).importAllComplexes();
				order.verify(locationJob).processPending();
			});
	}

	@Test
	void 좌표_월간_작업만_켜고_지오코딩을_끄면_시작에_실패한다() {
		contextRunner
			.withPropertyValues(
				"app.complex-import.schedule-enabled=true",
				"app.complex-location-import.schedule-enabled=true"
			)
			.run(context -> assertThat(context).hasFailed());
	}
}
