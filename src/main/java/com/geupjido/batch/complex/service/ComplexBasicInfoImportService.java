package com.geupjido.batch.complex.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.model.ComplexImportStatus;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;

/**
 * 공동주택 기본정보 한 건을 수집해 신규 단지만 저장한다.
 */
@Service
@ConditionalOnProperty(
	prefix = "app.external.complex-basic-info",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBasicInfoImportService {

	private final ComplexBasicInfoCollectionService collectionService;
	private final ComplexRepository complexRepository;

	public ComplexBasicInfoImportService(
		ComplexBasicInfoCollectionService collectionService,
		ComplexRepository complexRepository
	) {
		this.collectionService = collectionService;
		this.complexRepository = complexRepository;
	}

	public ComplexImportStatus importIfAbsent(
		String complexCode
	) {
		if (complexRepository.existsById(complexCode)) {
			return ComplexImportStatus.SKIPPED_ALREADY_EXISTS;
		}

		Complex complex =
			collectionService.collectByComplexCode(complexCode);

		complexRepository.save(complex);

		return ComplexImportStatus.CREATED;
	}
}
