package com.geupjido.batch.complex.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.geupjido.complex.domain.Complex;
import com.geupjido.zone.domain.Zone;
import com.geupjido.zone.repository.ZoneRepository;

import static com.geupjido.batch.complex.service.ComplexZoneMatchService.Status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComplexZoneMatchServiceTest {

	private final GeometryFactory geometryFactory =
		new GeometryFactory(new PrecisionModel(), 4326);

	private ZoneRepository zoneRepository;
	private ComplexZoneMatchService service;
	private Complex complex;
	private Zone zone;

	@BeforeEach
	void setUp() {
		zoneRepository = mock(ZoneRepository.class);
		service = new ComplexZoneMatchService(zoneRepository);
		complex = new Complex(
			"A10019883", "테스트 단지", "서울특별시 강남구 역삼동 1",
			"서울특별시 강남구 도곡로 1", "1168010100",
			100, null, null
		);
		MultiPolygon polygon = geometryFactory.createMultiPolygon(
			new org.locationtech.jts.geom.Polygon[] {
				geometryFactory.createPolygon(new Coordinate[] {
					new Coordinate(127.0, 37.0),
					new Coordinate(128.0, 37.0),
					new Coordinate(128.0, 38.0),
					new Coordinate(127.0, 38.0),
					new Coordinate(127.0, 37.0)
				})
			}
		);
		zone = new Zone(
			"gangnam-yeoksam", null, "역삼",
			new String[] {"1168010100"}, polygon, point(127.5, 37.5)
		);
	}

	@Test
	void 내부_좌표와_법정동_코드가_일치하면_매칭한다() {
		when(zoneRepository.findCoveringZones(127.5, 37.5))
			.thenReturn(List.of(zone));

		var result = service.match(complex, point(127.5, 37.5));

		assertThat(result.status()).isEqualTo(Status.MATCHED);
		assertThat(result.candidates()).containsExactly(zone);
	}

	@Test
	void 후보가_없으면_범위_밖으로_분류한다() {
		when(zoneRepository.findCoveringZones(129.0, 39.0))
			.thenReturn(List.of());

		var result = service.match(complex, point(129.0, 39.0));

		assertThat(result.status()).isEqualTo(Status.OUTSIDE);
		assertThat(result.candidates()).isEmpty();
	}

	@Test
	void 후보가_둘이면_자동_선택하지_않는다() {
		Zone other = new Zone(
			"other", null, "다른 권역", new String[] {"1168010100"},
			zone.getPolygon(), point(127.5, 37.5)
		);
		when(zoneRepository.findCoveringZones(127.5, 37.5))
			.thenReturn(List.of(zone, other));

		var result = service.match(complex, point(127.5, 37.5));

		assertThat(result.status()).isEqualTo(Status.MULTIPLE);
		assertThat(result.candidates()).containsExactly(zone, other);
	}

	@Test
	void 경계선_좌표는_자동_선택하지_않는다() {
		when(zoneRepository.findCoveringZones(127.0, 37.5))
			.thenReturn(List.of(zone));

		var result = service.match(complex, point(127.0, 37.5));

		assertThat(result.status()).isEqualTo(Status.BOUNDARY);
	}

	@Test
	void 좌표와_법정동_코드가_충돌하면_검토_대상으로_분류한다() {
		Zone mismatched = new Zone(
			"other", null, "다른 권역", new String[] {"1168010200"},
			zone.getPolygon(), point(127.5, 37.5)
		);
		when(zoneRepository.findCoveringZones(127.5, 37.5))
			.thenReturn(List.of(mismatched));

		var result = service.match(complex, point(127.5, 37.5));

		assertThat(result.status()).isEqualTo(Status.LEGAL_DONG_MISMATCH);
		assertThat(result.candidates()).containsExactly(mismatched);
	}

	@Test
	void 좌표계가_다르면_조회하지_않는다() {
		Point point = point(127.5, 37.5);
		point.setSRID(0);

		assertThatThrownBy(() -> service.match(complex, point))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private Point point(double longitude, double latitude) {
		return geometryFactory.createPoint(
			new Coordinate(longitude, latitude)
		);
	}
}
