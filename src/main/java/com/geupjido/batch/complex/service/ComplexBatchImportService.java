package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.exception.ComplexListApiException;
import com.geupjido.batch.complex.exception.ComplexApiQuotaExceededException;
import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportResult;
import com.geupjido.batch.complex.model.ComplexLegalDongImportStatus;

@Service
@ConditionalOnProperty(
	prefix = "app.external",
	name = {
		"complex-list.enabled",
		"complex-basic-info.enabled"
	},
	havingValue = "true"
)
public class ComplexBatchImportService {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexBatchImportService.class);

	private final ComplexBulkImportService bulkImportService;
	private final ComplexApiRetry apiRetry;

	public ComplexBatchImportService(
		ComplexBulkImportService bulkImportService,
		ComplexApiRetry apiRetry
	) {
		this.bulkImportService = bulkImportService;
		this.apiRetry = apiRetry;
	}

	public ComplexBatchImportResult importByLegalDongCodes(
		List<String> legalDongCodes
	) {
		validateLegalDongCodes(legalDongCodes);

		List<ComplexLegalDongImportResult> results =
			new ArrayList<>();

		for (String legalDongCode : legalDongCodes) {
			try {
				ComplexBulkImportResult result =
					apiRetry.execute(
						() -> bulkImportService.importByLegalDongCodes(
							List.of(legalDongCode)
						),
						exception ->
							exception instanceof ComplexListApiException listError
								&& "04".equals(listError.resultCode())
					);

				results.add(
					new ComplexLegalDongImportResult(
						legalDongCode,
						result,
						null
					)
				);
				logProgress(results, legalDongCodes.size());
			} catch (ComplexListApiException exception) {
				if ("22".equals(exception.resultCode())) {
					throw new ComplexApiQuotaExceededException(
						"공동주택 목록 API 호출 한도를 초과했습니다. 법정동 코드="
							+ legalDongCode,
						exception
					);
				}

				String reason = exception.getMessage();
				if (reason == null || reason.isBlank()) {
					reason = exception.getClass().getSimpleName();
				}

				results.add(
					new ComplexLegalDongImportResult(
						legalDongCode,
						new ComplexBulkImportResult(0, 0, 0),
						reason
					)
				);
				logProgress(results, legalDongCodes.size());
			}
		}

		return new ComplexBatchImportResult(results);
	}

	private static void logProgress(
		List<ComplexLegalDongImportResult> results,
		int totalLegalDongs
	) {
		ComplexLegalDongImportResult latest = results.get(results.size() - 1);
		log.info(
			"공동주택 단지 적재 진행: 법정동 {}/{}, 코드={}, 상태={}, "
				+ "신규={}, 건너뛰기={}, 실패={}",
			results.size(),
			totalLegalDongs,
			latest.legalDongCode(),
			latest.status(),
			latest.complexes().createdCount(),
			latest.complexes().skippedCount(),
			latest.complexes().failedCount()
		);
		if (latest.status() == ComplexLegalDongImportStatus.LIST_FAILED) {
			log.warn(
				"공동주택 목록 조회 실패: 법정동 코드={}, 사유={}",
				latest.legalDongCode(),
				latest.listFailureReason()
			);
		}
	}

	private static void validateLegalDongCodes(
		List<String> legalDongCodes
	) {
		if (legalDongCodes == null || legalDongCodes.isEmpty()) {
			throw new IllegalArgumentException(
				"법정동 코드 목록은 비어 있을 수 없습니다."
			);
		}

		Set<String> uniqueCodes = new HashSet<>();

		for (String code : legalDongCodes) {
			if (code == null || !code.matches("\\d{10}")) {
				throw new IllegalArgumentException(
					"법정동 코드는 숫자 10자리여야 합니다."
				);
			}
			if (!uniqueCodes.add(code)) {
				throw new IllegalArgumentException(
					"중복된 법정동 코드가 있습니다: " + code
				);
			}
		}
	}
}
