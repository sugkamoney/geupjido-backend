package com.geupjido.zone.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 권역 인접 관계 출처 enum과 데이터베이스 문자열을 변환한다.
 */
@Converter
public class ZoneAdjacencySourceConverter
	implements AttributeConverter<ZoneAdjacencySource, String> {

	@Override
	public String convertToDatabaseColumn(ZoneAdjacencySource source) {
		if (source == null) {
			return null;
		}
		return source.getDatabaseValue();
	}

	@Override
	public ZoneAdjacencySource convertToEntityAttribute(String databaseValue) {
		if (databaseValue == null) {
			return null;
		}

		return ZoneAdjacencySource.fromDatabaseValue(databaseValue);
	}
}
