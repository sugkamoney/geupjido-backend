package com.geupjido.match.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
	prefix = "app.worldcup-rating", name = "schedule-enabled", havingValue = "true"
)
public class WorldcupRatingSchedulingConfig {
}
