package com.geupjido.batch.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 아파트 매매 실거래가 API 연결에 필요한 설정값을 나타낸다.
 */
@ConfigurationProperties(prefix = "app.external.apartment-trade")
public record ApartmentTradeApiProperties(
	boolean enabled,
	String baseUrl,
	String serviceKey
) {
}
