package com.geupjido.batch.complex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공동주택 단지 목록 API 연결에 필요한 설정값을 나타낸다.
 */
@ConfigurationProperties(prefix = "app.external.complex-list")
public record ComplexListApiProperties(
	boolean enabled,
	String baseUrl,
	String serviceKey
) {
}
