package com.geupjido.batch.trade.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.apartment-trade-import")
public record ApartmentTradeImportProperties(
	boolean enabled,
	String districtCode,
	String dealYearMonth
) {
}
