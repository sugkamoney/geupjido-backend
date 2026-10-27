package com.geupjido.batch.complex.runner;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexImportFailure;
import com.geupjido.batch.complex.model.ComplexLegalDongImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportStatus;
import com.geupjido.batch.complex.service.ComplexImportJobService;

@Component
@ConditionalOnProperty(
	prefix = "app.complex-import",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBulkImportRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexBulkImportRunner.class);

	private final ComplexImportJobService jobService;

	public ComplexBulkImportRunner(ComplexImportJobService jobService) {
		this.jobService = jobService;
	}

	@Override
	public void run(ApplicationArguments args) {
		long startedAt = System.nanoTime();
		log.info("공동주택 단지 수동 적재를 시작합니다.");

		ComplexBatchImportResult result = jobService.importComplexes();

		for (ComplexLegalDongImportResult legalDong : result.legalDongs()) {
			for (ComplexImportFailure failure : legalDong.complexes().failures()) {
				log.warn(
					"공동주택 단지 적재에 실패했습니다. 법정동 코드={}, "
						+ "단지코드={}, 유형={}, 사유={}",
					legalDong.legalDongCode(),
					failure.complexCode(),
					failure.type(),
					failure.reason()
				);
			}
		}

		log.info(
			"공동주택 단지 수동 적재를 완료했습니다. 소요 시간={}초, "
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
