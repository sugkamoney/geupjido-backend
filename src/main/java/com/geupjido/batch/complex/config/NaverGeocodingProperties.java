package com.geupjido.batch.complex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 네이버 Maps Geocoding API 연결에 필요한 설정값을 나타낸다.
 */
@ConfigurationProperties(prefix = "app.external.geocoding")
public record NaverGeocodingProperties(
	boolean enabled,
	String baseUrl,
	String clientId,
	String clientSecret
) {
	@Override
	public String toString() {
		return "NaverGeocodingProperties[enabled=" + enabled
			+ ", baseUrl=" + baseUrl
			+ ", clientId=****, clientSecret=****]";
	}
}
