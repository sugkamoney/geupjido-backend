package com.geupjido.zone.domain;

/**
 * 권역 인접 관계가 생성된 방식을 나타낸다.
 */
public enum ZoneAdjacencySource {

	AUTO_CITY("auto_city"),
	AUTO_TOUCH("auto_touch"),
	MANUAL("manual");

	private final String databaseValue;

	ZoneAdjacencySource(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String getDatabaseValue() {
		return databaseValue;
	}

	public static ZoneAdjacencySource fromDatabaseValue(String databaseValue) {
		for (ZoneAdjacencySource source : values()) {
			if (source.databaseValue.equals(databaseValue)) {
				return source;
			}
		}

		throw new IllegalArgumentException(
			"지원하지 않는 권역 인접 관계 출처입니다: " + databaseValue
		);
	}
}
