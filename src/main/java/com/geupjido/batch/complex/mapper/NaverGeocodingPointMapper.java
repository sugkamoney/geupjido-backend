package com.geupjido.batch.complex.mapper;

import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;

@Component
public class NaverGeocodingPointMapper {

	private static final GeometryFactory GEOMETRY_FACTORY =
		new GeometryFactory(new PrecisionModel(), 4326);

	public Point map(NaverGeocodingResponse.Address address) {
		Objects.requireNonNull(address, "좌표 응답이 필요합니다.");

		double longitude = parse(address.x(), "경도");
		double latitude = parse(address.y(), "위도");

		if (longitude < -180 || longitude > 180
			|| latitude < -90 || latitude > 90) {
			throw new IllegalArgumentException(
				"경도 또는 위도가 유효 범위를 벗어났습니다."
			);
		}

		return GEOMETRY_FACTORY.createPoint(
			new Coordinate(longitude, latitude)
		);
	}

	private static double parse(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
				fieldName + " 값이 없습니다."
			);
		}
		try {
			double parsed = Double.parseDouble(value);
			if (!Double.isFinite(parsed)) {
				throw new NumberFormatException();
			}
			return parsed;
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(
				fieldName + " 값이 올바른 숫자가 아닙니다.",
				exception
			);
		}
	}
}
