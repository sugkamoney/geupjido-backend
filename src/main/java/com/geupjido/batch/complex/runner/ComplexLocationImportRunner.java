package com.geupjido.batch.complex.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.geupjido.batch.complex.service.ComplexLocationImportService;
import com.geupjido.zone.domain.Zone;

/**
 * 로컬에서 단지 한 건의 좌표·권역 적재를 수동 실행한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.complex-location-import",
	name = "enabled",
	havingValue = "true"
)
public class ComplexLocationImportRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexLocationImportRunner.class);

	private final Environment environment;
	private final ComplexLocationImportService importService;

	public ComplexLocationImportRunner(
		Environment environment,
		ComplexLocationImportService importService
	) {
		this.environment = environment;
		this.importService = importService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (environment.getProperty(
			"app.complex-location-import.batch-enabled", Boolean.class, false
		)) {
			throw new IllegalStateException(
				"단건 적재와 배치 적재를 동시에 활성화할 수 없습니다."
			);
		}

		String complexId = environment.getProperty(
			"app.complex-location-import.complex-id"
		);
		Assert.hasText(complexId, "적재할 단지 코드가 필요합니다.");

		ComplexLocationImportService.ProcessResult result =
			importService.processOne(complexId);

		log.info(
			"단지 좌표·권역 적재 결과: 단지코드={}, 처리={}, 권역 판정={}, 후보 권역={}",
			complexId,
			result.kind(),
			result.matchResult()
				.map(match -> match.status().name())
				.orElse("없음"),
			result.matchResult()
				.map(match -> match.candidates().stream()
					.map(Zone::getId)
					.toList())
				.orElseGet(java.util.List::of)
		);
	}
}
