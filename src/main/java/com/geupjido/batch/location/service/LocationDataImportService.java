package com.geupjido.batch.location.service;

import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongBoundary;
import com.geupjido.batch.location.model.LocationMapping;
import com.geupjido.batch.location.reader.LegalDongBoundaryReader;
import com.geupjido.batch.location.reader.LocationMappingReader;
import com.geupjido.batch.location.repository.LegalDongBoundaryRepository;
import com.geupjido.batch.location.repository.LocationDataImportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 지역·권역 매핑과 법정동 경계를 검증하고 PostGIS에 적재한다.
 */
@Service
public class LocationDataImportService {

	private final LocationMappingReader mappingReader;
	private final LegalDongBoundaryReader boundaryReader;
	private final LegalDongBoundaryRepository boundaryRepository;
	private final LocationDataImportRepository importRepository;

	public LocationDataImportService(
		LocationMappingReader mappingReader,
		LegalDongBoundaryReader boundaryReader,
		LegalDongBoundaryRepository boundaryRepository,
		LocationDataImportRepository importRepository
	) {
		this.mappingReader = mappingReader;
		this.boundaryReader = boundaryReader;
		this.boundaryRepository = boundaryRepository;
		this.importRepository = importRepository;
	}

	@Transactional
	public void importLocationData(
		Path mappingPath,
		Path boundaryPath,
		String codeProperty
	) {
		LocationMapping mapping = mappingReader.read(mappingPath);
		List<LegalDongBoundary> boundaries = boundaryReader.read(
			boundaryPath,
			codeProperty
		);

		validateBoundaryCoverage(mapping, boundaries);

		boundaryRepository.createTemporaryTable();
		boundaryRepository.saveAll(boundaries);

		importRepository.upsertRegions(mapping.regions());
		importRepository.upsertCities(mapping.cities());
		importRepository.upsertZones(mapping.zones());
	}

	private void validateBoundaryCoverage(
		LocationMapping mapping,
		List<LegalDongBoundary> boundaries
	) {
		Set<String> availableDongCodes = boundaries.stream()
			.map(LegalDongBoundary::dongCode)
			.collect(Collectors.toSet());

		List<String> missingDongCodes = mapping.zones().stream()
			.flatMap(zone -> zone.dongCodes().stream())
			.filter(dongCode -> !availableDongCodes.contains(dongCode))
			.sorted()
			.toList();

		if (!missingDongCodes.isEmpty()) {
			throw new LocationMappingException(
				"GeoJSON에 매핑 대상 법정동 경계가 없습니다: "
					+ String.join(", ", missingDongCodes)
			);
		}
	}
}
