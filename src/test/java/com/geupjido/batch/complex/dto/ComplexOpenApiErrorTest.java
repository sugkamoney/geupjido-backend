package com.geupjido.batch.complex.dto;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ComplexOpenApiErrorTest {

	private static final String GATEWAY_ERROR = """
		{
		  "OpenAPI_ServiceResponse": {
		    "cmmMsgHeader": {
		      "returnReasonCode": "04",
		      "returnAuthMsg": "HTTP 에러",
		      "errMsg": "SERVICE ERROR"
		    }
		  }
		}
		""";

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void 목록_API의_게이트웨이_오류를_읽는다() throws Exception {
		ComplexListApiResponse response = mapper.readValue(
			GATEWAY_ERROR,
			ComplexListApiResponse.class
		);

		assertThat(response.response()).isNull();
		assertThat(response.gatewayError().cmmMsgHeader().returnReasonCode())
			.isEqualTo("04");
		assertThat(response.gatewayError().cmmMsgHeader().returnAuthMsg())
			.isEqualTo("HTTP 에러");
	}

	@Test
	void 기본정보_API의_게이트웨이_오류를_읽는다() throws Exception {
		ComplexBasicInfoApiResponse response = mapper.readValue(
			GATEWAY_ERROR,
			ComplexBasicInfoApiResponse.class
		);

		assertThat(response.response()).isNull();
		assertThat(response.gatewayError().cmmMsgHeader().returnReasonCode())
			.isEqualTo("04");
	}
}
