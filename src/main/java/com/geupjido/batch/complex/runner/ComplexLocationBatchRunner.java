package com.geupjido.batch.complex.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.service.ComplexLocationBatchService;

/**
 * 명시적으로 활성화할 때만 단지 좌표·권역을 제한된 건수만큼 수동 적재한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
	prefix = "app.complex-location-import",
	name = "batch-enabled",
	havingValue = "true"
)
public class ComplexLocationBatchRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexLocationBatchRunner.class);

	private final Environment environment;
	private final ComplexLocationBatchService batchService;

	public ComplexLocationBatchRunner(
		Environment environment,
		ComplexLocationBatchService batchService
	) {
		this.environment = environment;
		this.batchService = batchService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (environment.getProperty(
			"app.complex-location-import.enabled", Boolean.class, false
		)) {
			throw new IllegalStateException(
				"단건 적재와 배치 적재를 동시에 활성화할 수 없습니다."
			);
		}

		String afterId = environment.getProperty(
			"app.complex-location-import.batch-after-id", ""
		);
		int maxCount = environment.getProperty(
			"app.complex-location-import.batch-max-count", Integer.class, 1
		);

		log.info(
			"단지 좌표·권역 수동 배치를 시작합니다. 시작 커서={}, 최대 처리 건수={}",
			afterId, maxCount
		);
		ComplexLocationBatchService.BatchResult result =
			batchService.processBatch(afterId, maxCount);

		for (ComplexLocationBatchService.ProcessedComplex processed : result.processed()) {
			log.info(
				"단지 좌표·권역 배치 결과: 단지코드={}, 처리={}, 권역 판정={}",
				processed.complexId(),
				processed.result().kind(),
				processed.result().matchResult()
					.map(match -> match.status().name())
					.orElse("없음")
			);
		}
		for (ComplexLocationBatchService.BatchFailure failure : result.failures()) {
			log.warn(
				"단지 좌표·권역 배치 보류: 단지코드={}, 사유={}",
				failure.complexId(), failure.kind()
			);
		}

		log.info(
			"단지 좌표·권역 수동 배치를 완료했습니다. 처리 건수={}, 보류 건수={}, 다음 시작 커서={}",
			result.processed().size(), result.failures().size(),
			result.nextAfterId()
		);
	}
}
