package com.geupjido.batch.complex.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;
import com.geupjido.batch.complex.exception.ComplexApiQuotaExceededException;
import com.geupjido.batch.complex.exception.ComplexBasicInfoMappingException;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexImportFailure;
import com.geupjido.batch.complex.model.ComplexImportFailureType;
import com.geupjido.batch.complex.model.ComplexImportStatus;

/**
 * 법정동별 공동주택 단지 목록을 수집하고
 * 각 단지의 기본정보를 신규 저장한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external",
	name = {
		"complex-list.enabled",
		"complex-basic-info.enabled"
	},
	havingValue = "true"
)
public class ComplexBulkImportService {

	private final ComplexListCollectionService listCollectionService;
	private final ComplexBasicInfoImportService basicInfoImportService;
	private final ComplexApiRetry apiRetry;

	public ComplexBulkImportService(
		ComplexListCollectionService listCollectionService,
		ComplexBasicInfoImportService basicInfoImportService,
		ComplexApiRetry apiRetry
	) {
		this.listCollectionService = listCollectionService;
		this.basicInfoImportService = basicInfoImportService;
		this.apiRetry = apiRetry;
	}

	/**
	 * 여러 법정동의 단지 목록을 수집하고
	 * 각 단지의 기본정보를 신규 저장한다.
	 */
	public ComplexBulkImportResult importByLegalDongCodes(
		List<String> legalDongCodes
	) {
		List<ComplexListApiItem> complexItems =
			listCollectionService.collectByLegalDongCodes(
				legalDongCodes
			);

		int createdCount = 0;
		int skippedCount = 0;
		List<ComplexImportFailure> failures =
			new ArrayList<>();

		for (ComplexListApiItem complexItem : complexItems) {
			try {
				ComplexImportStatus status =
					importWithRetry(
						complexItem.kaptCode()
					);

				switch (status) {
					case CREATED -> createdCount++;
					case SKIPPED_ALREADY_EXISTS, SKIPPED_RENTAL_ONLY,
						SKIPPED_STAFF_HOUSING,
						SKIPPED_SALE_TYPE_UNKNOWN ->
						skippedCount++;
				}
			} catch (ComplexBasicInfoApiException exception) {
				if ("22".equals(exception.resultCode())) {
					throw new ComplexApiQuotaExceededException(
						"공동주택 기본정보 API 호출 한도를 초과했습니다. 단지 코드="
							+ complexItem.kaptCode(),
						exception
					);
				}

				failures.add(
					new ComplexImportFailure(
						complexItem.kaptCode(),
						ComplexImportFailureType.API,
						failureReason(exception)
					)
				);
			} catch (
				ComplexBasicInfoMappingException
				| IllegalArgumentException exception
			) {
				failures.add(
					new ComplexImportFailure(
						complexItem.kaptCode(),
						ComplexImportFailureType.MAPPING,
						failureReason(exception)
					)
				);
			}
		}

		return new ComplexBulkImportResult(
			complexItems.size(),
			createdCount,
			skippedCount,
			failures
		);
	}

	private ComplexImportStatus importWithRetry(
		String complexCode
	) {
		return apiRetry.execute(
			() -> basicInfoImportService.importIfAbsent(complexCode),
			exception ->
				exception instanceof ComplexBasicInfoApiException apiError
					&& ("04".equals(apiError.resultCode())
						|| apiError.getCause() instanceof RestClientException)
		);
	}

	private static String failureReason(
		RuntimeException exception
	) {
		String message = exception.getMessage();

		if (message == null || message.isBlank()) {
			return exception.getClass().getSimpleName();
		}

		return message;
	}
}
