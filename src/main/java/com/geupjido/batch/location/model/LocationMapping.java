package com.geupjido.batch.location.model;

import com.geupjido.batch.location.exception.LocationMappingException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 법정동 경계를 서비스의 지역·시군구·권역으로 묶기 위한 기준 데이터다.
 */
public record LocationMapping(
	List<RegionDefinition> regions,
	List<CityDefinition> cities,
	List<ZoneDefinition> zones
) {
	private static final String CITY_CODE_PATTERN = "\\d{5}";
	private static final String DONG_CODE_PATTERN = "\\d{10}";

	public void validate() {
		if (regions == null || regions.isEmpty()) {
			throw new LocationMappingException("region이 하나 이상 필요합니다.");
		}

		if (cities == null || cities.isEmpty()) {
			throw new LocationMappingException("city가 하나 이상 필요합니다.");
		}

		if (zones == null || zones.isEmpty()) {
			throw new LocationMappingException("zone이 하나 이상 필요합니다.");
		}

		validateUniqueValues(
			regions.stream().map(RegionDefinition::code).toList(),
			"region code"
		);

		validateUniqueValues(
			cities.stream().map(CityDefinition::id).toList(),
			"city id"
		);

		validateUniqueValues(
			zones.stream().map(ZoneDefinition::id).toList(),
			"zone id"
		);

		Set<String> regionCodes = new HashSet<>(
			regions.stream()
				.map(RegionDefinition::code)
				.toList()
		);

		Set<String> cityIds = new HashSet<>(
			cities.stream()
				.map(CityDefinition::id)
				.toList()
		);

		for (CityDefinition city : cities) {
			if (!city.id().matches(CITY_CODE_PATTERN)) {
				throw new LocationMappingException(
					"city id는 5자리 숫자여야 합니다: " + city.id()
				);
			}

			if (!regionCodes.contains(city.regionCode())) {
				throw new LocationMappingException(
					"존재하지 않는 region을 참조합니다: " + city.id()
				);
			}
		}

		Set<String> assignedDongCodes = new HashSet<>();

		for (ZoneDefinition zone : zones) {
			if (!cityIds.contains(zone.cityId())) {
				throw new LocationMappingException(
					"존재하지 않는 city를 참조합니다: " + zone.id()
				);
			}

			if (zone.dongCodes() == null || zone.dongCodes().isEmpty()) {
				throw new LocationMappingException(
					"zone에는 법정동 코드가 하나 이상 필요합니다: " + zone.id()
				);
			}

			for (String dongCode : zone.dongCodes()) {
				if (dongCode == null || !dongCode.matches(DONG_CODE_PATTERN)) {
					throw new LocationMappingException(
						"법정동 코드는 10자리 숫자여야 합니다: " + dongCode
					);
				}

				if (!dongCode.startsWith(zone.cityId())) {
					throw new LocationMappingException(
						"법정동 코드와 city id가 일치하지 않습니다: " + dongCode
					);
				}

				if (!assignedDongCodes.add(dongCode)) {
					throw new LocationMappingException(
						"법정동 코드가 둘 이상의 zone에 배정되었습니다: " + dongCode
					);
				}
			}
		}
	}

	private static void validateUniqueValues(List<String> values, String label) {
		Set<String> uniqueValues = new HashSet<>();

		for (String value : values) {
			if (value == null || value.isBlank()) {
				throw new LocationMappingException(
					label + "은(는) 비어 있을 수 없습니다."
				);
			}

			if (!uniqueValues.add(value)) {
				throw new LocationMappingException(
					label + "이(가) 중복되었습니다: " + value
				);
			}
		}
	}


	public record RegionDefinition(
		String code,
		String name,
		boolean active,
		int displayOrder
	) {

	}

	public record CityDefinition(
		String id,
		String regionCode,
		String name
	) {
	}

	public record ZoneDefinition(
		String id,
		String cityId,
		String name,
		List<String> dongCodes
	) {
	}
}
