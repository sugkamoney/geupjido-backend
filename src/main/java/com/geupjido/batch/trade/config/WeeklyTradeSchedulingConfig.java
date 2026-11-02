package com.geupjido.batch.trade.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 주간 실거래가 예약 실행을 명시적으로 켠 환경에서만 활성화한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
	prefix = "app.weekly-trade-refresh",
	name = "schedule-enabled",
	havingValue = "true"
)
public class WeeklyTradeSchedulingConfig {
}
