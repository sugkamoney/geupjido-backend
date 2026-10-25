package com.geupjido.batch.complex.runner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.config.ComplexBulkImportProperties;
import com.geupjido.batch.complex.model.ComplexBulkImportResult;
import com.geupjido.batch.complex.model.ComplexImportFailure;
import com.geupjido.batch.complex.service.ComplexBulkImportService;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.reader.LocationMappingReader;

/**
 * 설정으로 활성화된 경우 권역 매핑을 기준으로
 * 공동주택 기본정보 초기 적재를 실행한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.complex-import",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBulkImportRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(ComplexBulkImportRunner.class);

	private final ComplexBulkImportProperties properties;
	private final LocationMappingReader mappingReader;
	private final ComplexBulkImportService importService;

	public ComplexBulkImportRunner(
		ComplexBulkImportProperties properties,
		LocationMappingReader mappingReader,
		ComplexBulkImportService importService
	) {
		this.properties = properties;
		this.mappingReader = mappingReader;
		this.importService = importService;
	}

	@Override
	public void run(ApplicationArguments args) {
		validateMappingFile();

		LocationMapping mapping =
			mappingReader.read(properties.mappingPath());

		List<String> legalDongCodes =
			extractLegalDongCodes(mapping, properties.legalDongCodes());

		log.info(
			"공동주택 기본정보 초기 적재를 시작합니다. 법정동 코드 수={}",
			legalDongCodes.size()
		);

		ComplexBulkImportResult result =
			importService.importByLegalDongCodes(
				legalDongCodes
			);

		for (ComplexImportFailure failure : result.failures()) {
			log.warn(
				"공동주택 기본정보 적재에 실패했습니다. "
					+ "단지코드={}, 유형={}, 사유={}",
				failure.complexCode(),
				failure.type(),
				failure.reason()
			);
		}

		log.info(
			"공동주택 기본정보 초기 적재를 완료했습니다. "
				+ "전체={}, 신규={}, 건너뛰기={}, 실패={}",
			result.totalCount(),
			result.createdCount(),
			result.skippedCount(),
			result.failedCount()
		);
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

	private void validateMappingFile() {
		Path mappingPath = properties.mappingPath();

		if (
			mappingPath == null
				|| mappingPath.toString().isBlank()
		) {
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
}
