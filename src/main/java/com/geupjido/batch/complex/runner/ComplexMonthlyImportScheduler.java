package com.geupjido.batch.complex.runner;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportStatus;
import com.geupjido.batch.complex.service.ComplexImportJobService;

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

	public ComplexMonthlyImportScheduler(ComplexImportJobService jobService) {
		this.jobService = jobService;
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
	}
}
