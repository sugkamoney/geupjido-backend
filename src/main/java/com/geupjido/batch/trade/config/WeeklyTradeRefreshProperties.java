package com.geupjido.batch.trade.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.weekly-trade-refresh")
public record WeeklyTradeRefreshProperties(
	boolean runOnStartup,
	boolean scheduleEnabled,
	boolean smokeRunOnStartup,
	String smokeDistrictCode,
	String smokeDealYearMonth,
	Path mappingPath,
	Path referencePath,
	int monthsToCollect,
	Path reportPath
) {
}
