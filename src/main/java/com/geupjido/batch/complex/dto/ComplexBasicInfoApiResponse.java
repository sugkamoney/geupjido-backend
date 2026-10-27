package com.geupjido.batch.complex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 공동주택 단지 기본정보 API의 전체 응답 구조를 나타낸다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplexBasicInfoApiResponse(
	Result response,
	@JsonProperty("OpenAPI_ServiceResponse") ComplexOpenApiError gatewayError
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Result(
		Header header,
		Body body
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Header(
		String resultCode,
		String resultMsg
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(
		ComplexBasicInfoApiItem item
	) {
	}
}
