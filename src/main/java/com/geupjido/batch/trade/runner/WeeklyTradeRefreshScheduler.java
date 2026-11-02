package com.geupjido.batch.trade.runner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geupjido.batch.trade.service.WeeklyTradeRefreshJob;

/** 한국 시간 기준 주 1회 실거래 수집과 대표 시세 갱신을 실행한다. */
@Component
@ConditionalOnProperty(
	prefix = "app.weekly-trade-refresh",
	name = "schedule-enabled",
	havingValue = "true"
)
public class WeeklyTradeRefreshScheduler {

	private final WeeklyTradeRefreshJob job;

	public WeeklyTradeRefreshScheduler(WeeklyTradeRefreshJob job) {
		this.job = job;
	}

	@Scheduled(
		cron = "${app.weekly-trade-refresh.schedule-cron:0 0 3 * * MON}",
		zone = "Asia/Seoul"
	)
	public void refreshWeekly() {
		job.execute();
	}
}
