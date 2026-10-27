package com.geupjido.batch.complex.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;
import com.geupjido.batch.complex.mapper.NaverGeocodingPointMapper;
import com.geupjido.complex.domain.Complex;
import com.geupjido.complex.repository.ComplexRepository;
import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ComplexLocationImportServiceTest {

	private static final String COMPLEX_ID = "A10019883";

	private final GeometryFactory geometryFactory =
		new GeometryFactory(new PrecisionModel(), 4326);

	private ComplexRepository complexRepository;
	private ComplexGeocodingLookupService lookupService;
	private NaverGeocodingPointMapper pointMapper;
	private ComplexLocationPersistenceService persistenceService;
	private ComplexLocationImportService service;
	private Complex complex;

	@BeforeEach
	void setUp() {
		complexRepository = mock(ComplexRepository.class);
		lookupService = mock(ComplexGeocodingLookupService.class);
		pointMapper = mock(NaverGeocodingPointMapper.class);
		persistenceService = mock(ComplexLocationPersistenceService.class);
		service = new ComplexLocationImportService(
			complexRepository, lookupService, pointMapper, persistenceService
		);
		complex = new Complex(
			COMPLEX_ID, "테스트 단지", "서울특별시 강남구 역삼동 1",
			"서울특별시 강남구 도곡로 1", "1168010100",
			100, null, null
		);
		when(complexRepository.findById(COMPLEX_ID))
			.thenReturn(Optional.of(complex));
	}

	@Test
	void 이미_권역이_배정된_단지는_API를_호출하지_않는다() {
		complex.updateLocation(point());
		complex.assignZone(mock(Zone.class));

		var result = service.processOne(COMPLEX_ID);

		assertThat(result.kind())
			.isEqualTo(ComplexLocationImportService.Kind.ALREADY_ASSIGNED);
		assertThat(result.matchResult()).isEmpty();
		verifyNoInteractions(lookupService, pointMapper, persistenceService);
	}

	@Test
	void 저장된_좌표가_있으면_API_없이_권역만_재판정한다() {
		complex.updateLocation(point());
		var match = new ComplexZoneMatchService.MatchResult(
			ComplexZoneMatchService.Status.OUTSIDE, List.of()
		);
		when(persistenceService.assignZoneIfAbsent(COMPLEX_ID))
			.thenReturn(Optional.of(match));

		var result = service.processOne(COMPLEX_ID);

		assertThat(result.kind()).isEqualTo(
			ComplexLocationImportService.Kind.EXISTING_LOCATION_RECHECKED
		);
		assertThat(result.matchResult()).contains(match);
		verify(persistenceService).assignZoneIfAbsent(COMPLEX_ID);
		verifyNoInteractions(lookupService, pointMapper);
	}

	@Test
	void 좌표가_없으면_지오코딩한_뒤_저장한다() {
		var address = new NaverGeocodingResponse.Address(
			"도로명주소", "지번주소", "127.5", "37.5"
		);
		Point point = point();
		var match = new ComplexZoneMatchService.MatchResult(
			ComplexZoneMatchService.Status.OUTSIDE, List.of()
		);
		when(lookupService.findAddress(complex))
			.thenReturn(Optional.of(address));
		when(pointMapper.map(address)).thenReturn(point);
		when(persistenceService.saveLocationIfAbsent(COMPLEX_ID, point))
			.thenReturn(Optional.of(match));

		var result = service.processOne(COMPLEX_ID);

		assertThat(result.kind()).isEqualTo(
			ComplexLocationImportService.Kind.NEW_LOCATION_SAVED
		);
		assertThat(result.matchResult()).contains(match);
		verify(persistenceService).saveLocationIfAbsent(COMPLEX_ID, point);
	}

	@Test
	void 주소_조회_결과가_없으면_저장하지_않는다() {
		when(lookupService.findAddress(complex))
			.thenReturn(Optional.empty());

		var result = service.processOne(COMPLEX_ID);

		assertThat(result.kind()).isEqualTo(
			ComplexLocationImportService.Kind.GEOCODING_NOT_FOUND
		);
		assertThat(result.matchResult()).isEmpty();
		verifyNoInteractions(pointMapper, persistenceService);
	}

	private Point point() {
		return geometryFactory.createPoint(
			new Coordinate(127.5, 37.5)
		);
	}
}
