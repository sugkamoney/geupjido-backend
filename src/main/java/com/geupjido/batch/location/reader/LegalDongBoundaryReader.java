package com.geupjido.batch.location.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geupjido.batch.location.exception.LocationMappingException;
import com.geupjido.batch.location.model.LegalDongBoundary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * GeoJSON FeatureCollection에서 법정동 코드와 경계 도형을 읽는다.
 */
@Component
public class LegalDongBoundaryReader {

	private static final String EMD_CODE_PATTERN = "\\d{8}";
	private static final String DONG_CODE_PATTERN = "\\d{10}";

	private final ObjectMapper objectMapper;

	public LegalDongBoundaryReader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public List<LegalDongBoundary> read(
		Path path,
		String codeProperty
	) {
		if (codeProperty == null || codeProperty.isBlank()) {
			throw new LocationMappingException(
				"법정동 코드 속성명은 비어 있을 수 없습니다."
			);
		}

		try {
			JsonNode root = objectMapper.readTree(path.toFile());

			validateFeatureCollection(root);

			List<LegalDongBoundary> boundaries = new ArrayList<>();
			Set<String> dongCodes = new HashSet<>();

			for (JsonNode feature : root.path("features")) {
				LegalDongBoundary boundary = readFeature(
					feature,
					codeProperty
				);

				if (!dongCodes.add(boundary.dongCode())) {
					throw new LocationMappingException(
						"GeoJSON에 중복된 법정동 코드가 있습니다: "
							+ boundary.dongCode()
					);
				}

				boundaries.add(boundary);
			}

			return List.copyOf(boundaries);
		} catch (IOException exception) {
			throw new LocationMappingException(
				"법정동 GeoJSON 파일을 읽을 수 없습니다: " + path,
				exception
			);
		}
	}

	private void validateFeatureCollection(JsonNode root) {
		if (!"FeatureCollection".equals(root.path("type").asText())) {
			throw new LocationMappingException(
				"GeoJSON type은 FeatureCollection이어야 합니다."
			);
		}

		JsonNode features = root.path("features");

		if (!features.isArray() || features.size() == 0) {
			throw new LocationMappingException(
				"GeoJSON features가 비어 있거나 배열이 아닙니다."
			);
		}
	}

	private LegalDongBoundary readFeature(
		JsonNode feature,
		String codeProperty
	) {
		if (!"Feature".equals(feature.path("type").asText())) {
			throw new LocationMappingException(
				"features의 항목은 Feature여야 합니다."
			);
		}

		String dongCode = normalizeDongCode(
			feature.path("properties")
				.path(codeProperty)
				.asText()
		);

		JsonNode geometry = feature.path("geometry");
		String geometryType = geometry.path("type").asText();

		if (!"Polygon".equals(geometryType)
			&& !"MultiPolygon".equals(geometryType)) {
			throw new LocationMappingException(
				"법정동 경계는 Polygon 또는 MultiPolygon이어야 합니다: "
					+ dongCode
			);
		}

		if (!geometry.path("coordinates").isArray()) {
			throw new LocationMappingException(
				"법정동 경계 coordinates가 배열이 아닙니다: "
					+ dongCode
			);
		}

		return new LegalDongBoundary(
			dongCode,
			geometry.toString()
		);
	}

	private static String normalizeDongCode(String dongCode) {
		if (dongCode.matches(DONG_CODE_PATTERN)) {
			return dongCode;
		}

		if (dongCode.matches(EMD_CODE_PATTERN)) {
			return dongCode + "00";
		}

		throw new LocationMappingException(
			"GeoJSON의 법정동 코드는 8자리 또는 10자리 숫자여야 합니다: "
				+ dongCode
		);
	}
}
