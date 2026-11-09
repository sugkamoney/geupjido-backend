package com.geupjido.match.api;

import java.math.BigDecimal;

import com.geupjido.match.model.WorldcupZoneSelection;

public record WorldcupZoneSelectionResponse(
	String zoneId,
	String cityName,
	String zoneName,
	BigDecimal tier,
	long activeComplexCount,
	boolean canStart
) {
	public static WorldcupZoneSelectionResponse from(WorldcupZoneSelection zone) {
		return new WorldcupZoneSelectionResponse(
			zone.zoneId(), zone.cityName(), zone.zoneName(), zone.tier(),
			zone.activeComplexCount(), zone.canStart()
		);
	}
}
