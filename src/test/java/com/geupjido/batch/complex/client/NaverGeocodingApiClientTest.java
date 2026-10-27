package com.geupjido.batch.complex.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverGeocodingApiClientTest {

	private MockRestServiceServer server;
	private NaverGeocodingApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl("https://maps.example.com/map-geocode/v2");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new NaverGeocodingApiClient(builder.build());
	}

	@Test
	void 한글_주소를_쿼리로_전달하고_경도와_위도를_문자열로_읽는다() {
		String address = "서울특별시 강남구 도곡로21길 15";
		server.expect(request -> {
			assertThat(request.getURI().getPath())
				.isEqualTo("/map-geocode/v2/geocode");
			assertThat(request.getURI().getQuery())
				.isEqualTo("query=" + address);
		})
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(
				"""
				{
				  "status": "OK",
				  "meta": { "totalCount": 1, "page": 1, "count": 1 },
				  "addresses": [{
				    "roadAddress": "서울특별시 강남구 도곡로21길 15",
				    "jibunAddress": "서울특별시 강남구 역삼동 795-41",
				    "x": "127.0350000",
				    "y": "37.4950000",
				    "addressElements": []
				  }]
				}
				""",
				MediaType.APPLICATION_JSON
			));

		NaverGeocodingResponse response = client.geocode(address);

		assertThat(response.meta().totalCount()).isEqualTo(1);
		assertThat(response.addresses()).hasSize(1);
		assertThat(response.addresses().getFirst().x())
			.isEqualTo("127.0350000");
		assertThat(response.addresses().getFirst().y())
			.isEqualTo("37.4950000");
		server.verify();
	}

	@Test
	void 빈_주소는_API를_호출하지_않는다() {
		assertThatThrownBy(() -> client.geocode(" "))
			.isInstanceOf(IllegalArgumentException.class);
		server.verify();
	}
}
