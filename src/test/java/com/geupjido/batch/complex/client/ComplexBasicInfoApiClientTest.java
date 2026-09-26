package com.geupjido.batch.complex.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.complex.config.ComplexBasicInfoApiProperties;
import com.geupjido.batch.complex.dto.ComplexBasicInfoApiItem;
import com.geupjido.batch.complex.exception.ComplexBasicInfoApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ComplexBasicInfoApiClientTest {

	private MockRestServiceServer server;
	private ComplexBasicInfoApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://example.com");

		server = MockRestServiceServer.bindTo(builder).build();

		ComplexBasicInfoApiProperties properties =
			new ComplexBasicInfoApiProperties(
				true,
				"https://example.com",
				"test-service-key"
			);

		client = new ComplexBasicInfoApiClient(
			builder.build(),
			properties
		);
	}

	@Test
	void 단지_코드로_기본정보를_조회한다() {
		server.expect(requestTo(
				"https://example.com/getAphusBassInfoV5"
					+ "?serviceKey=test-service-key"
					+ "&kaptCode=A10027875"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
				    "body": {
				      "item": {
				        "kaptCode": "A10027875",
				        "kaptName": "괴정 경성스마트W아파트",
				        "kaptAddr": "부산광역시 사하구 괴정동 258",
				        "doroJuso": "부산광역시 사하구 낙동대로 180",
				        "kaptDongCnt": "3",
				        "kaptdaCnt": 182.0,
				        "kaptUsedate": "20150806",
				        "bjdCode": "2638010100",
				        "codeHeatNm": "개별난방"
				      }
				    },
				    "header": {
				      "resultCode": "00",
				      "resultMsg": "NORMAL SERVICE."
				    }
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		ComplexBasicInfoApiItem result =
			client.fetchByComplexCode("A10027875");

		assertThat(result.kaptCode()).isEqualTo("A10027875");
		assertThat(result.kaptName())
			.isEqualTo("괴정 경성스마트W아파트");
		assertThat(result.kaptAddr())
			.isEqualTo("부산광역시 사하구 괴정동 258");
		assertThat(result.doroJuso())
			.isEqualTo("부산광역시 사하구 낙동대로 180");
		assertThat(result.kaptDongCnt()).isEqualTo("3");
		assertThat(result.kaptdaCnt())
			.isEqualByComparingTo("182.0");
		assertThat(result.kaptUsedate()).isEqualTo("20150806");
		assertThat(result.bjdCode()).isEqualTo("2638010100");

		server.verify();
	}

	@Test
	void API가_오류_코드를_반환하면_예외가_발생한다() {
		server.expect(requestTo(
				"https://example.com/getAphusBassInfoV5"
					+ "?serviceKey=test-service-key"
					+ "&kaptCode=A10027875"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
					"body": null,
					"header": {
					  "resultCode": "10",
					  "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"
					}
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		assertThatThrownBy(
			() -> client.fetchByComplexCode("A10027875")
		)
			.isInstanceOf(ComplexBasicInfoApiException.class)
			.hasMessageContaining("resultCode=10")
			.hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");

		server.verify();
	}

	@Test
	void 잘못된_단지_코드는_API_호출_전에_거부한다() {
		assertThatThrownBy(
			() -> client.fetchByComplexCode("A100-27875")
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("1~20자의 영문자와 숫자");

		server.verify();
	}

	@Test
	void 정상_응답에_item이_없으면_예외가_발생한다() {
		server.expect(requestTo(
				"https://example.com/getAphusBassInfoV5"
					+ "?serviceKey=test-service-key"
					+ "&kaptCode=A10027875"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
					"body": {
					  "item": null
					},
					"header": {
					  "resultCode": "00",
					  "resultMsg": "NORMAL SERVICE."
					}
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		assertThatThrownBy(
			() -> client.fetchByComplexCode("A10027875")
		)
			.isInstanceOf(ComplexBasicInfoApiException.class)
			.hasMessageContaining("item이 없습니다");

		server.verify();
	}
}
