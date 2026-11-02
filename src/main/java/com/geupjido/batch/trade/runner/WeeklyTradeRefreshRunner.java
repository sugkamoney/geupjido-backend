package com.geupjido.batch.trade.runner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.service.WeeklyTradeRefreshJob;

/** 명시적으로 켠 경우 앱 시작 시 주간 갱신 흐름을 한 번 실행한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.weekly-trade-refresh",
	name = "run-on-startup",
	havingValue = "true"
)
public class WeeklyTradeRefreshRunner implements ApplicationRunner {

	private final WeeklyTradeRefreshJob job;

	public WeeklyTradeRefreshRunner(WeeklyTradeRefreshJob job) {
		this.job = job;
	}

	@Override
	public void run(ApplicationArguments args) {
		job.execute();
	}
}
