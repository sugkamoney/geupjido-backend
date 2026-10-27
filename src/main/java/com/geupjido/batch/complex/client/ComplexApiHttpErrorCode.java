package com.geupjido.batch.complex.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

/** HTTP 오류 본문에 담긴 공공데이터포털 결과 코드를 추출한다. */
final class ComplexApiHttpErrorCode {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private ComplexApiHttpErrorCode() {
	}

	static String extract(RestClientResponseException exception) {
		try {
			JsonNode root = OBJECT_MAPPER.readTree(
				exception.getResponseBodyAsByteArray()
			);
			JsonNode gatewayCode = root.path("OpenAPI_ServiceResponse")
				.path("cmmMsgHeader")
				.path("returnReasonCode");
			if (!gatewayCode.isMissingNode() && !gatewayCode.isNull()) {
				return normalize(gatewayCode.asText());
			}
			JsonNode responseCode = root.path("response")
				.path("header")
				.path("resultCode");
			return responseCode.isMissingNode() || responseCode.isNull()
				? null : normalize(responseCode.asText());
		} catch (Exception ignored) {
			return null;
		}
	}

	static String normalize(String resultCode) {
		return resultCode != null && resultCode.matches("\\d{2}")
			? resultCode : null;
	}
}
