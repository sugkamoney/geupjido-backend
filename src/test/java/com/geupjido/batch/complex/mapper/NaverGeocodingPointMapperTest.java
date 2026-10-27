package com.geupjido.batch.complex.mapper;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaverGeocodingPointMapperTest {

	private final NaverGeocodingPointMapper mapper =
		new NaverGeocodingPointMapper();

	@Test
	void 경도와_위도를_SRID_4326_Point로_변환한다() {
		Point point = mapper.map(address("127.0350000", "37.4950000"));

		assertThat(point.getX()).isEqualTo(127.035);
		assertThat(point.getY()).isEqualTo(37.495);
		assertThat(point.getSRID()).isEqualTo(4326);
	}

	@Test
	void 경도와_위도를_뒤집은_값은_거부한다() {
		assertThatThrownBy(
			() -> mapper.map(address("37.4950000", "127.0350000"))
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("유효 범위");
	}

	@Test
	void 숫자가_아닌_좌표는_거부한다() {
		assertThatThrownBy(() -> mapper.map(address("NaN", "37.495")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("경도");
	}

	private static NaverGeocodingResponse.Address address(
		String longitude,
		String latitude
	) {
		return new NaverGeocodingResponse.Address(
			null,
			null,
			longitude,
			latitude
		);
	}
}
