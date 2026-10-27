package com.geupjido.batch.complex.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 공동주택 월간 배치를 명시적으로 켠 환경에서만 스케줄링을 활성화한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
	prefix = "app.complex-import",
	name = "schedule-enabled",
	havingValue = "true"
)
public class ComplexMonthlySchedulingConfig {
}
