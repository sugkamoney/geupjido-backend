package com.geupjido.batch.complex.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공동주택 기본정보 초기 일괄 적재에 필요한 설정을 관리한다.
 */
@ConfigurationProperties(prefix = "app.complex-import")
public record ComplexBulkImportProperties(
	boolean enabled,
	Path mappingPath
) {
}
