package com.geupjido.batch.complex.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 공공데이터포털 게이트웨이가 정상 응답 대신 보내는 오류 본문이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ComplexOpenApiError(
	Header cmmMsgHeader
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Header(
		String returnReasonCode,
		String returnAuthMsg
	) {
	}
}
