package com.geupjido.batch.complex.runner;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportStatus;
import com.geupjido.batch.complex.service.ComplexImportJobService;
import com.geupjido.batch.complex.service.ComplexLocationJobService;

@Component
@ConditionalOnProperty(
	prefix = "app.complex-import",
	name = "schedule-enabled",
	havingValue = "true"
)
public class ComplexMonthlyImportScheduler {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexMonthlyImportScheduler.class);

	private final ComplexImportJobService jobService;
	private final ObjectProvider<ComplexLocationJobService> locationJobProvider;
	private final Environment environment;

	public ComplexMonthlyImportScheduler(
		ComplexImportJobService jobService,
		ObjectProvider<ComplexLocationJobService> locationJobProvider,
		Environment environment
	) {
		this.jobService = jobService;
		this.locationJobProvider = locationJobProvider;
		this.environment = environment;
		if (environment.getProperty(
			"app.complex-location-import.schedule-enabled", Boolean.class, false
		) && locationJobProvider.getIfAvailable() == null) {
			throw new IllegalStateException(
				"월간 단지 좌표·권역 작업에는 네이버 Geocoding 활성화가 필요합니다."
			);
		}
	}

	@Scheduled(
		cron = "${app.complex-import.schedule-cron:0 0 2 1 * *}",
		zone = "Asia/Seoul"
	)
	public void importMonthly() {
		long startedAt = System.nanoTime();
		log.info("공동주택 단지 월간 적재를 시작합니다.");

		ComplexBatchImportResult result = jobService.importAllComplexes();

		log.info(
			"공동주택 단지 월간 적재를 완료했습니다. 소요 시간={}초, "
				+ "법정동 전체={}, 성공={}, 일부 실패={}, 빈 목록={}, 목록 실패={}, "
				+ "단지 전체={}, 신규={}, 건너뛰기={}, 실패={}, 재시도 법정동={}",
			TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt),
			result.legalDongs().size(),
			result.count(ComplexLegalDongImportStatus.SUCCESS),
			result.count(ComplexLegalDongImportStatus.PARTIAL),
			result.count(ComplexLegalDongImportStatus.EMPTY),
			result.count(ComplexLegalDongImportStatus.LIST_FAILED),
			result.totalComplexCount(),
			result.createdComplexCount(),
			result.skippedComplexCount(),
			result.failedComplexCount(),
			result.retryLegalDongCodes()
		);

		if (!environment.getProperty(
			"app.complex-location-import.schedule-enabled", Boolean.class, false
		)) {
			return;
		}
		ComplexLocationJobService locationJob = locationJobProvider.getIfAvailable();
		if (locationJob == null) {
			throw new IllegalStateException(
				"월간 단지 좌표·권역 작업에는 네이버 Geocoding 활성화가 필요합니다."
			);
		}

		long locationStartedAt = System.nanoTime();
		log.info("공동주택 단지 월간 좌표·권역 작업을 시작합니다.");
		ComplexLocationJobService.JobResult locationResult =
			locationJob.processPending();
		log.info(
			"공동주택 단지 월간 좌표·권역 작업을 완료했습니다. "
				+ "소요 시간={}초, 처리={}, 권역 배정={}, 미배정={}, "
				+ "복수 결과 보류={}, 건너뛰기={}, 마지막 커서={}",
			TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - locationStartedAt),
			locationResult.processedCount(),
			locationResult.matchedCount(),
			locationResult.unassignedCount(),
			locationResult.heldCount(),
			locationResult.skippedCount(),
			locationResult.lastCompletedId()
		);
	}
}
