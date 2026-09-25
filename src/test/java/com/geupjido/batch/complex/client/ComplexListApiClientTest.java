package com.geupjido.batch.complex.client;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.complex.config.ComplexListApiProperties;
import com.geupjido.batch.complex.dto.ComplexListApiItem;
import com.geupjido.batch.complex.exception.ComplexListApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ComplexListApiClientTest {

	private MockRestServiceServer server;
	private ComplexListApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://example.com");

		server = MockRestServiceServer.bindTo(builder).build();

		ComplexListApiProperties properties =
			new ComplexListApiProperties(
				true,
				"https://example.com",
				"test-service-key"
			);

		client = new ComplexListApiClient(
			builder.build(),
			properties
		);
	}

	@Test
	void 법정동_코드로_단지_목록을_조회한다() {
		server.expect(requestTo(
				"https://example.com/getLegaldongAptList4"
					+ "?serviceKey=test-service-key"
					+ "&pageNo=1"
					+ "&numOfRows=100"
					+ "&bjdCode=1168010700"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
				    "header": {
				      "resultCode": "00",
				      "resultMsg": "NORMAL SERVICE."
				    },
				    "body": {
				      "items": [
				        {
				          "kaptCode": "A10000001",
				          "kaptName": "테스트아파트",
				          "bjdCode": "1168010700",
				          "as1": "서울특별시",
				          "as2": "강남구",
				          "as3": "신사동",
				          "as4": null
				        },
				        {
				          "kaptCode": "A10000002",
				          "kaptName": "샘플아파트",
				          "bjdCode": "1168010700"
				        }
				      ],
				      "numOfRows": 100,
				      "pageNo": 1,
				      "totalCount": 2
				    }
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		List<ComplexListApiItem> result =
			client.fetchAllByLegalDongCode("1168010700");

		assertThat(result).hasSize(2);
		assertThat(result.get(0).kaptCode()).isEqualTo("A10000001");
		assertThat(result.get(0).kaptName()).isEqualTo("테스트아파트");
		assertThat(result.get(0).bjdCode()).isEqualTo("1168010700");
		assertThat(result.get(1).kaptCode()).isEqualTo("A10000002");

		server.verify();
	}

	@Test
	void 조회_결과가_없으면_빈_목록을_반환한다() {
		server.expect(requestTo(
				"https://example.com/getLegaldongAptList4"
					+ "?serviceKey=test-service-key"
					+ "&pageNo=1"
					+ "&numOfRows=100"
					+ "&bjdCode=1111010100"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
					"header": {
					  "resultCode": "00",
					  "resultMsg": "NORMAL SERVICE."
					},
					"body": {
					  "items": null,
					  "numOfRows": 100,
					  "pageNo": 1,
					  "totalCount": 0
					}
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		List<ComplexListApiItem> result =
			client.fetchAllByLegalDongCode("1111010100");

		assertThat(result).isEmpty();

		server.verify();
	}

	@Test
	void API가_오류_코드를_반환하면_예외가_발생한다() {
		server.expect(requestTo(
				"https://example.com/getLegaldongAptList4"
					+ "?serviceKey=test-service-key"
					+ "&pageNo=1"
					+ "&numOfRows=100"
					+ "&bjdCode=1168010700"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "response": {
					"header": {
					  "resultCode": "10",
					  "resultMsg": "INVALID_REQUEST_PARAMETER_ERROR"
					},
					"body": null
				  }
				}
				""",
				MediaType.APPLICATION_JSON
			));

		assertThatThrownBy(
			() -> client.fetchAllByLegalDongCode("1168010700")
		)
			.isInstanceOf(ComplexListApiException.class)
			.hasMessageContaining("resultCode=10")
			.hasMessageContaining("INVALID_REQUEST_PARAMETER_ERROR");

		server.verify();
	}

	@Test
	void 잘못된_법정동_코드는_API_호출_전에_거부한다() {
		assertThatThrownBy(
			() -> client.fetchAllByLegalDongCode("11680107")
		)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("숫자 10자리");

		server.verify();
	}

	@Test
	void 여러_페이지의_단지_목록을_하나로_합친다() {
		server.expect(requestTo(
				"https://example.com/getLegaldongAptList4"
					+ "?serviceKey=test-service-key"
					+ "&pageNo=1"
					+ "&numOfRows=100"
					+ "&bjdCode=1168010700"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				successResponse(
					"A10000001",
					"첫번째아파트",
					1,
					2
				),
				MediaType.APPLICATION_JSON
			));

		server.expect(requestTo(
				"https://example.com/getLegaldongAptList4"
					+ "?serviceKey=test-service-key"
					+ "&pageNo=2"
					+ "&numOfRows=100"
					+ "&bjdCode=1168010700"
			))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				successResponse(
					"A10000002",
					"두번째아파트",
					2,
					2
				),
				MediaType.APPLICATION_JSON
			));

		List<ComplexListApiItem> result =
			client.fetchAllByLegalDongCode("1168010700");

		assertThat(result)
			.extracting(ComplexListApiItem::kaptCode)
			.containsExactly("A10000001", "A10000002");

		server.verify();
	}

	private static String successResponse(
		String kaptCode,
		String kaptName,
		int pageNo,
		int totalCount
	) {
		return """
		{
		  "response": {
		    "header": {
		      "resultCode": "00",
		      "resultMsg": "NORMAL SERVICE."
		    },
		    "body": {
		      "items": [
		        {
		          "kaptCode": "%s",
		          "kaptName": "%s",
		          "bjdCode": "1168010700"
		        }
		      ],
		      "numOfRows": 100,
		      "pageNo": %d,
		      "totalCount": %d
		    }
		  }
		}
		""".formatted(
			kaptCode,
			kaptName,
			pageNo,
			totalCount
		);
	}
}
