package com.geupjido.batch.complex.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

import com.geupjido.complex.domain.Complex;
import com.geupjido.zone.domain.Zone;
import com.geupjido.zone.repository.ZoneRepository;

@Service
public class ComplexZoneMatchService {

	private final ZoneRepository zoneRepository;

	public ComplexZoneMatchService(ZoneRepository zoneRepository) {
		this.zoneRepository = zoneRepository;
	}

	public MatchResult match(Complex complex, Point point) {
		Objects.requireNonNull(complex, "단지가 필요합니다.");
		Objects.requireNonNull(point, "좌표가 필요합니다.");

		if (point.isEmpty() || point.getSRID() != 4326) {
			throw new IllegalArgumentException("SRID 4326 좌표가 필요합니다.");
		}

		List<Zone> candidates = zoneRepository.findCoveringZones(
			point.getX(), point.getY()
		);

		if (candidates.isEmpty()) {
			return new MatchResult(Status.OUTSIDE, List.of());
		}
		if (candidates.size() > 1) {
			return new MatchResult(Status.MULTIPLE, List.copyOf(candidates));
		}

		Zone zone = candidates.getFirst();
		if (!zone.getPolygon().contains(point)) {
			return new MatchResult(Status.BOUNDARY, List.of(zone));
		}
		if (!Arrays.asList(zone.getDongCodes())
			.contains(complex.getLegalDongCode())) {
			return new MatchResult(
				Status.LEGAL_DONG_MISMATCH, List.of(zone)
			);
		}

		return new MatchResult(Status.MATCHED, List.of(zone));
	}

	public enum Status {
		MATCHED,
		OUTSIDE,
		MULTIPLE,
		BOUNDARY,
		LEGAL_DONG_MISMATCH
	}

	public record MatchResult(Status status, List<Zone> candidates) {
	}
}
