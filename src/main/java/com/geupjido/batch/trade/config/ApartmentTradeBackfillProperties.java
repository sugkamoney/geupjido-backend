package com.geupjido.batch.trade.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.apartment-trade-backfill")
public record ApartmentTradeBackfillProperties(
	boolean enabled,
	Path mappingPath,
	String fromMonth,
	String throughMonth,
	int maxPairs,
	Path manifestPath
) {
}
