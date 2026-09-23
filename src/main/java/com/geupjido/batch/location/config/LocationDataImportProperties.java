package com.geupjido.batch.location.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 지역·권역 초기 데이터 적재에 필요한 설정을 관리한다.
 */
@ConfigurationProperties(prefix = "app.location-import")
public record LocationDataImportProperties(
	boolean enabled,
	Path mappingPath,
	Path boundaryPath,
	String codeProperty
) {
}
