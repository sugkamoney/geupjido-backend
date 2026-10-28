package com.geupjido.batch.trade.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.apartment-trade-match")
public record ApartmentTradeMatchProperties(
	boolean enabled,
	boolean auditEnabled,
	Path auditPath,
	Path proposalPath,
	Path referencePath,
	Path mappingPath
) {
}
