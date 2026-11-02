package com.geupjido.complex.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import com.geupjido.zone.domain.Zone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ComplexActivityTest {

	private final GeometryFactory geometryFactory =
		new GeometryFactory(new PrecisionModel(), 4326);

	@Test
	void 일반_매매_가격과_카드_정보가_모두_갖춰지면_활성화한다() {
		Complex complex = complex(300, LocalDate.of(2015, 8, 6));
		complex.updateRepresentativeTrade(250_000,
			new BigDecimal("59.987654"), LocalDate.of(2026, 10, 20));
		assertThat(complex.isActive()).isFalse();

		complex.updateLocation(point(127.0, 37.0));
		assertThat(complex.isActive()).isFalse();

		complex.assignZone(mock(Zone.class));
		assertThat(complex.isActive()).isTrue();
	}

	@Test
	void 가격이나_권역이_사라지면_비활성화하고_다시_갖추면_활성화한다() {
		Complex complex = completeComplex();
		assertThat(complex.isActive()).isTrue();

		complex.updateRepresentativeTrade(null, null, null);
		assertThat(complex.isActive()).isFalse();
		complex.updateRepresentativeTrade(250_000,
			new BigDecimal("59.987654"), LocalDate.of(2026, 10, 20));
		assertThat(complex.isActive()).isTrue();

		complex.updateLocation(point(127.1, 37.1));
		assertThat(complex.isActive()).isFalse();
		complex.assignZone(mock(Zone.class));
		assertThat(complex.isActive()).isTrue();
	}

	@Test
	void 세대수가_300미만이거나_사용승인일이_없으면_활성화하지_않는다() {
		Complex zeroHouseholds = completeComplex(0, LocalDate.of(2015, 8, 6));
		Complex underMinimum = completeComplex(299, LocalDate.of(2015, 8, 6));
		Complex atMinimum = completeComplex(300, LocalDate.of(2015, 8, 6));
		Complex noApprovalDate = completeComplex(300, null);

		assertThat(zeroHouseholds.isActive()).isFalse();
		assertThat(underMinimum.isActive()).isFalse();
		assertThat(atMinimum.isActive()).isTrue();
		assertThat(noApprovalDate.isActive()).isFalse();
	}

	@Test
	void 분양권_입주권_가격만_있으면_활성화하지_않는다() {
		Complex complex = complex(300, LocalDate.of(2015, 8, 6));
		complex.updateLocation(point(127.0, 37.0));
		complex.assignZone(mock(Zone.class));
		complex.updateRepresentativeTrade(250_000,
			new BigDecimal("59.987654"), LocalDate.of(2026, 10, 20),
			RepresentativePriceSource.PRESALE_OR_OCCUPANCY_RIGHT);
		assertThat(complex.isActive()).isFalse();

		complex.updateRepresentativeTrade(250_000,
			new BigDecimal("59.987654"), LocalDate.of(2026, 10, 20),
			RepresentativePriceSource.APARTMENT_SALE);
		assertThat(complex.isActive()).isTrue();
	}

	private Complex completeComplex() {
		return completeComplex(300, LocalDate.of(2015, 8, 6));
	}

	private Complex completeComplex(int households, LocalDate approvalDate) {
		Complex complex = complex(households, approvalDate);
		complex.updateLocation(point(127.0, 37.0));
		complex.assignZone(mock(Zone.class));
		complex.updateRepresentativeTrade(250_000,
			new BigDecimal("59.987654"), LocalDate.of(2026, 10, 20));
		return complex;
	}

	private Complex complex(int households, LocalDate approvalDate) {
		return new Complex("A10027875", "테스트 단지", "서울시 테스트동 1",
			null, "1168010100", households, null, approvalDate);
	}

	private org.locationtech.jts.geom.Point point(double x, double y) {
		return geometryFactory.createPoint(new Coordinate(x, y));
	}
}
