package com.geupjido.batch.complex.service;

import java.util.Optional;

import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geupjido.batch.complex.service.ComplexZoneMatchService.MatchResult;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.Status;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;

@Service
public class ComplexLocationPersistenceService {

	private final ComplexRepository complexRepository;
	private final ComplexZoneMatchService zoneMatchService;

	public ComplexLocationPersistenceService(
		ComplexRepository complexRepository,
		ComplexZoneMatchService zoneMatchService
	) {
		this.complexRepository = complexRepository;
		this.zoneMatchService = zoneMatchService;
	}

	@Transactional
	public Optional<MatchResult> saveLocationIfAbsent(
		String complexId,
		Point point
	) {
		Complex complex = complexRepository.findById(complexId)
			.orElseThrow(() -> new IllegalArgumentException(
				"단지를 찾을 수 없습니다: " + complexId
			));

		if (complex.getLocation() != null) {
			return Optional.empty();
		}

		MatchResult result = zoneMatchService.match(complex, point);
		complex.updateLocation(point);

		if (result.status() == Status.MATCHED) {
			complex.assignZone(result.candidates().getFirst());
		}

		return Optional.of(result);
	}

	@Transactional
	public Optional<MatchResult> assignZoneIfAbsent(String complexId) {
		Complex complex = complexRepository.findById(complexId)
			.orElseThrow(() -> new IllegalArgumentException(
				"단지를 찾을 수 없습니다: " + complexId
			));

		if (complex.getLocation() == null || complex.getZone() != null) {
			return Optional.empty();
		}

		MatchResult result = zoneMatchService.match(
			complex, complex.getLocation()
		);
		if (result.status() == Status.MATCHED) {
			complex.assignZone(result.candidates().getFirst());
		}

		return Optional.of(result);
	}

}
