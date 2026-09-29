package com.geupjido.batch.complex.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
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

	public ComplexBulkImportService(
		ComplexListCollectionService listCollectionService,
		ComplexBasicInfoImportService basicInfoImportService
	) {
		this.listCollectionService = listCollectionService;
		this.basicInfoImportService = basicInfoImportService;
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

		for (ComplexListApiItem complexItem : complexItems) {
			ComplexImportStatus status =
				basicInfoImportService.importIfAbsent(
					complexItem.kaptCode()
				);

			switch (status) {
				case CREATED -> createdCount++;
				case SKIPPED_ALREADY_EXISTS -> skippedCount++;
			}
		}

		return new ComplexBulkImportResult(
			complexItems.size(),
			createdCount,
			skippedCount
		);
	}
}
