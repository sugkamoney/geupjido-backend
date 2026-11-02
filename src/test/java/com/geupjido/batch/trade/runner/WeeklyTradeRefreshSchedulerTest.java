package com.geupjido.batch.trade.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Scheduled;

import com.geupjido.batch.trade.config.WeeklyTradeSchedulingConfig;
import com.geupjido.batch.trade.service.WeeklyTradeRefreshJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WeeklyTradeRefreshSchedulerTest {

	@Test
	void 예약_실행도_공통_작업을_호출한다() {
		WeeklyTradeRefreshJob job = mock(WeeklyTradeRefreshJob.class);

		new WeeklyTradeRefreshScheduler(job).refreshWeekly();

		verify(job).execute();
	}

	@Test
	void 한국_시간_기준_주간_예약식을_사용한다() throws Exception {
		Scheduled scheduled = WeeklyTradeRefreshScheduler.class
			.getMethod("refreshWeekly").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron())
			.isEqualTo("${app.weekly-trade-refresh.schedule-cron:0 0 3 * * MON}");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void 기본적으로_예약_실행을_등록하지_않는다() {
		new ApplicationContextRunner()
			.withUserConfiguration(
				WeeklyTradeSchedulingConfig.class,
				WeeklyTradeRefreshScheduler.class
			)
			.withPropertyValues("app.weekly-trade-refresh.schedule-enabled=false")
			.run(context -> {
				assertThat(context)
					.doesNotHaveBean(WeeklyTradeSchedulingConfig.class);
				assertThat(context)
					.doesNotHaveBean(WeeklyTradeRefreshScheduler.class);
			});
	}
}
