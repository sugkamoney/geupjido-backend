package com.geupjido.batch.trade.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.geupjido.batch.trade.service.WeeklyTradeRefreshJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WeeklyTradeRefreshRunnerTest {

	@Test
	void 수동_실행은_공통_작업을_한_번_호출한다() {
		WeeklyTradeRefreshJob job = mock(WeeklyTradeRefreshJob.class);

		new WeeklyTradeRefreshRunner(job).run(null);

		verify(job).execute();
	}

	@Test
	void 기본적으로_Runner를_등록하지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(WeeklyTradeRefreshRunner.class)
			.withPropertyValues("app.weekly-trade-refresh.run-on-startup=false")
			.run(context -> assertThat(context)
				.doesNotHaveBean(WeeklyTradeRefreshRunner.class));
	}
}
