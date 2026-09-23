package com.geupjido.batch.location;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocationMappingTest {

	@Test
	void 같은_법정동을_둘_이상의_권역에_배정할_수_없다() {
		LocationMapping mapping = new LocationMapping(
			List.of(
				new LocationMapping.RegionDefinition(
					"seoul",
					"서울",
					true,
					1
				)
			),
			List.of(
				new LocationMapping.CityDefinition(
					"11680",
					"seoul",
					"강남구"
				)
			),
			List.of(
				new LocationMapping.ZoneDefinition(
					"gangnam-apgujeong",
					"11680",
					"압구정",
					List.of("1168010700")
				),
				new LocationMapping.ZoneDefinition(
					"gangnam-cheongdam",
					"11680",
					"청담",
					List.of("1168010700")
				)
			)
		);

		assertThatThrownBy(mapping::validate)
			.isInstanceOf(LocationMappingException.class)
			.hasMessageContaining("둘 이상의 zone");
	}
}
