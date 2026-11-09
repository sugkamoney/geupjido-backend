package com.geupjido.match.repository;

import java.math.BigDecimal;
import java.util.List;

public interface WorldcupZoneSelectionRepository {

	List<ZoneRow> findAll();

	List<String> findAdjacentZoneIds(String zoneId);

	record ZoneRow(
		String zoneId, String cityName, String zoneName,
		BigDecimal tier, long activeComplexCount
	) {
	}
}
