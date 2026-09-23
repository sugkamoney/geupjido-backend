package com.geupjido.batch.location.runner;

import com.geupjido.batch.location.config.LocationDataImportProperties;
import com.geupjido.batch.location.service.LocationDataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 설정으로 활성화된 경우 지역·권역 초기 데이터 적재를 실행한다.
 */
@Component
@ConditionalOnProperty(
	prefix = "app.location-import",
	name = "enabled",
	havingValue = "true"
)
public class LocationDataImportRunner implements ApplicationRunner {

	private static final Logger log =
		LoggerFactory.getLogger(LocationDataImportRunner.class);

	private final LocationDataImportProperties properties;
	private final LocationDataImportService importService;

	public LocationDataImportRunner(
		LocationDataImportProperties properties,
		LocationDataImportService importService
	) {
		this.properties = properties;
		this.importService = importService;
	}

	@Override
	public void run(ApplicationArguments args) {
		validateProperties();

		log.info("지역·권역 초기 데이터 적재를 시작합니다.");

		importService.importLocationData(
			properties.mappingPath(),
			properties.boundaryPath(),
			properties.codeProperty()
		);

		log.info("지역·권역 초기 데이터 적재를 완료했습니다.");
	}

	private void validateProperties() {
		validateFile(properties.mappingPath(), "매핑 JSON 경로");
		validateFile(properties.boundaryPath(), "법정동 경계 GeoJSON 경로");

		if (
			properties.codeProperty() == null
				|| properties.codeProperty().isBlank()
		) {
			throw new IllegalStateException(
				"GeoJSON 법정동 코드 속성명이 설정되지 않았습니다."
			);
		}
	}

	private void validateFile(Path path, String label) {
		if (path == null || path.toString().isBlank()) {
			throw new IllegalStateException(label + "가 설정되지 않았습니다.");
		}

		if (!Files.isRegularFile(path)) {
			throw new IllegalStateException(
				label + "에 파일이 존재하지 않습니다: " + path
			);
		}
	}
}
