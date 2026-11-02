package com.geupjido.zone.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.geupjido.zone.service.ZoneAdjacencyAutoTouchService;

/** 로컬에서 명시적으로 켠 경우에만 자동 접경 관계를 갱신한다. */
@Component
@Profile("local")
@ConditionalOnProperty(
	prefix = "app.zone-adjacency-auto-touch",
	name = "enabled",
	havingValue = "true"
)
public class ZoneAdjacencyAutoTouchRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ZoneAdjacencyAutoTouchRunner.class);

	private final ZoneAdjacencyAutoTouchService service;

	public ZoneAdjacencyAutoTouchRunner(ZoneAdjacencyAutoTouchService service) {
		this.service = service;
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("권역 자동 접경 관계 갱신 시작");
		service.refreshTouchingZoneAdjacencies();
		log.info("권역 자동 접경 관계 갱신 완료");
	}
}
