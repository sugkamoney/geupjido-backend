package com.geupjido.report.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.weekly-report", name = "schedule-enabled", havingValue = "true")
public class WeeklyReportSchedulingConfig {
}
