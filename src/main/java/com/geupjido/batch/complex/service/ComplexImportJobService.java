package com.geupjido.batch.complex.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.config.ComplexBulkImportProperties;
import com.geupjido.batch.complex.model.ComplexBatchImportResult;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.reader.LocationMappingReader;

@Service
@ConditionalOnProperty(
	prefix = "app.external",
	name = {
		"complex-list.enabled",
		"complex-basic-info.enabled"
	},
	havingValue = "true"
)
public class ComplexImportJobService {

	private final ComplexBulkImportProperties properties;
	private final LocationMappingReader mappingReader;
	private final ComplexBatchImportService batchImportService;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public ComplexImportJobService(
		ComplexBulkImportProperties properties,
		LocationMappingReader mappingReader,
		ComplexBatchImportService batchImportService
	) {
		this.properties = properties;
		this.mappingReader = mappingReader;
		this.batchImportService = batchImportService;
	}

	public ComplexBatchImportResult importComplexes() {
		return importComplexes(properties.legalDongCodes());
	}

	public ComplexBatchImportResult importAllComplexes() {
		return importComplexes(List.of());
	}

	private ComplexBatchImportResult importComplexes(List<String> selectedCodes) {
		if (!running.compareAndSet(false, true)) {
			throw new IllegalStateException(
				"공동주택 단지 적재가 이미 실행 중입니다."
			);
		}

		try {
			validateMappingFile();

			LocationMapping mapping =
				mappingReader.read(properties.mappingPath());

			List<String> legalDongCodes =
				extractLegalDongCodes(
					mapping,
					selectedCodes
				);

			return batchImportService.importByLegalDongCodes(
				legalDongCodes
			);
		} finally {
			running.set(false);
		}
	}

	private void validateMappingFile() {
		Path mappingPath = properties.mappingPath();

		if (mappingPath == null
			|| mappingPath.toString().isBlank()) {
			throw new IllegalStateException(
				"공동주택 초기 적재 매핑 파일 경로가 설정되지 않았습니다."
			);
		}

		if (!Files.isRegularFile(mappingPath)) {
			throw new IllegalStateException(
				"공동주택 초기 적재 매핑 파일이 존재하지 않습니다: "
					+ mappingPath
			);
		}
	}

	private static List<String> extractLegalDongCodes(
		LocationMapping mapping,
		List<String> selectedCodes
	) {
		List<String> mappedCodes = mapping.zones()
			.stream()
			.flatMap(zone -> zone.dongCodes().stream())
			.toList();

		if (selectedCodes == null || selectedCodes.isEmpty()) {
			return mappedCodes;
		}

		Set<String> mappedCodeSet = new HashSet<>(mappedCodes);
		Set<String> selectedCodeSet = new HashSet<>();

		for (String code : selectedCodes) {
			if (code == null || !code.matches("\\d{10}")) {
				throw new IllegalArgumentException(
					"법정동 코드는 숫자 10자리여야 합니다."
				);
			}
			if (!selectedCodeSet.add(code)) {
				throw new IllegalArgumentException(
					"중복된 법정동 코드가 있습니다: " + code
				);
			}
			if (!mappedCodeSet.contains(code)) {
				throw new IllegalArgumentException(
					"권역 매핑에 없는 법정동 코드입니다: " + code
				);
			}
		}

		return List.copyOf(selectedCodes);
	}
}
