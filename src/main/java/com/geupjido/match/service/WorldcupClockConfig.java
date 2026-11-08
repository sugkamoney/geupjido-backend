package com.geupjido.match.service;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldcupClockConfig {

	@Bean
	public Clock worldcupClock() {
		return Clock.systemUTC();
	}
}
