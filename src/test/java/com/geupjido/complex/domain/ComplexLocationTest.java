package com.geupjido.complex.domain;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ComplexLocationTest {

	private final GeometryFactory geometryFactory =
		new GeometryFactory(new PrecisionModel(), 4326);

	@Test
	void 좌표를_복사해_저장하고_기존_권역을_해제한다() {
		Complex complex = complex();
		Point first = point(127.0, 37.0);
		complex.updateLocation(first);
		complex.assignZone(mock(Zone.class));

		Point updated = point(127.5, 37.5);
		complex.updateLocation(updated);

		assertThat(complex.getLocation()).isNotSameAs(updated);
		assertThat(complex.getLocation().getX()).isEqualTo(127.5);
		assertThat(complex.getLocation().getY()).isEqualTo(37.5);
		assertThat(complex.getLocation().getSRID()).isEqualTo(4326);
		assertThat(complex.getZone()).isNull();
	}

	@Test
	void 좌표가_없으면_권역을_배정할_수_없다() {
		assertThatThrownBy(() -> complex().assignZone(mock(Zone.class)))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 잘못된_좌표계는_저장할_수_없다() {
		Point invalid = point(127.0, 37.0);
		invalid.setSRID(0);

		assertThatThrownBy(() -> complex().updateLocation(invalid))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private Complex complex() {
		return new Complex(
			"A10019883", "테스트 단지", "서울특별시 강남구 역삼동 1",
			"서울특별시 강남구 도곡로 1", "1168010100",
			100, null, null
		);
	}

	private Point point(double longitude, double latitude) {
		return geometryFactory.createPoint(
			new Coordinate(longitude, latitude)
		);
	}
}
