package com.geupjido.batch.complex.service;

import java.util.Optional;

import org.locationtech.jts.geom.Point;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;
import com.geupjido.batch.complex.mapper.NaverGeocodingPointMapper;
import com.geupjido.batch.complex.service.ComplexZoneMatchService.MatchResult;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;

@Service
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class ComplexLocationImportService {

	private final ComplexRepository complexRepository;
	private final ComplexGeocodingLookupService lookupService;
	private final NaverGeocodingPointMapper pointMapper;
	private final ComplexLocationPersistenceService persistenceService;

	public ComplexLocationImportService(
		ComplexRepository complexRepository,
		ComplexGeocodingLookupService lookupService,
		NaverGeocodingPointMapper pointMapper,
		ComplexLocationPersistenceService persistenceService
	) {
		this.complexRepository = complexRepository;
		this.lookupService = lookupService;
		this.pointMapper = pointMapper;
		this.persistenceService = persistenceService;
	}

	public ProcessResult processOne(String complexId) {
		Complex complex = complexRepository.findById(complexId)
			.orElseThrow(() -> new IllegalArgumentException(
				"단지를 찾을 수 없습니다: " + complexId
			));

		if (complex.getLocation() != null) {
			if (complex.getZone() != null) {
				return new ProcessResult(
					Kind.ALREADY_ASSIGNED, Optional.empty()
				);
			}

			Optional<MatchResult> match =
				persistenceService.assignZoneIfAbsent(complexId);
			return match
				.map(result -> new ProcessResult(
					Kind.EXISTING_LOCATION_RECHECKED,
					Optional.of(result)
				))
				.orElseGet(() -> new ProcessResult(
					Kind.SKIPPED_CONCURRENT_UPDATE,
					Optional.empty()
				));
		}

		Optional<NaverGeocodingResponse.Address> address =
			lookupService.findAddress(complex);
		if (address.isEmpty()) {
			return new ProcessResult(
				Kind.GEOCODING_NOT_FOUND, Optional.empty()
			);
		}

		Point point = pointMapper.map(address.orElseThrow());
		Optional<MatchResult> saved =
			persistenceService.saveLocationIfAbsent(complexId, point);

		return saved
			.map(result -> new ProcessResult(
				Kind.NEW_LOCATION_SAVED,
				Optional.of(result)
			))
			.orElseGet(() -> new ProcessResult(
				Kind.SKIPPED_CONCURRENT_UPDATE,
				Optional.empty()
			));
	}

	public enum Kind {
		ALREADY_ASSIGNED,
		EXISTING_LOCATION_RECHECKED,
		GEOCODING_NOT_FOUND,
		NEW_LOCATION_SAVED,
		SKIPPED_CONCURRENT_UPDATE
	}

	public record ProcessResult(
		Kind kind,
		Optional<MatchResult> matchResult
	) {
	}
}
