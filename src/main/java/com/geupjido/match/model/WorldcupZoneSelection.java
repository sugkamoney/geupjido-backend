package com.geupjido.match.model;

import java.math.BigDecimal;

public record WorldcupZoneSelection(
	String zoneId,
	String cityName,
	String zoneName,
	BigDecimal tier,
	long activeComplexCount,
	boolean canStart
) {
}
