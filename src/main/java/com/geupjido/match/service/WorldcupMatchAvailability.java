package com.geupjido.match.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.geupjido.match.model.MatchZoneCandidate;

@Component
public class WorldcupMatchAvailability {

	private static final BigDecimal DEFAULT_RANGE = new BigDecimal("0.2");
	private static final BigDecimal EXTENDED_RANGE = new BigDecimal("0.3");

	public Optional<BigDecimal> tierRange(
		MatchZoneCandidate base, List<MatchZoneCandidate> zones
	) {
		long count = base.activeComplexCount();
		if (count == 0) {
			return Optional.empty();
		}
		long within = Math.min(4, count * (count - 1) / 2);
		long requiredCross = 10 - within;
		if (crossPairs(base, zones, DEFAULT_RANGE) >= requiredCross) {
			return Optional.of(DEFAULT_RANGE);
		}
		if (crossPairs(base, zones, EXTENDED_RANGE) >= requiredCross) {
			return Optional.of(EXTENDED_RANGE);
		}
		return Optional.empty();
	}

	private long crossPairs(
		MatchZoneCandidate base, List<MatchZoneCandidate> zones, BigDecimal range
	) {
		return zones.stream()
			.filter(zone -> !zone.zoneId().equals(base.zoneId()))
			.filter(zone -> zone.tier().subtract(base.tier()).abs().compareTo(range) <= 0)
			.mapToLong(zone -> base.activeComplexCount() * zone.activeComplexCount())
			.sum();
	}
}
