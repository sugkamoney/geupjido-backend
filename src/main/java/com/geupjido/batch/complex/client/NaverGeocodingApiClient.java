package com.geupjido.batch.complex.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.geupjido.batch.complex.dto.NaverGeocodingResponse;

@Component
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class NaverGeocodingApiClient {

	private final RestClient restClient;

	public NaverGeocodingApiClient(
		@Qualifier("naverGeocodingRestClient") RestClient restClient
	) {
		this.restClient = restClient;
	}

	public NaverGeocodingResponse geocode(String address) {
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("검색할 주소가 필요합니다.");
		}

		NaverGeocodingResponse response = restClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/geocode")
				.queryParam("query", address)
				.build())
			.retrieve()
			.body(NaverGeocodingResponse.class);

		if (response == null
			|| !"OK".equals(response.status())
			|| response.meta() == null
			|| response.addresses() == null) {
			throw new IllegalStateException(
				"네이버 Geocoding 응답 구조가 올바르지 않습니다."
			);
		}

		return response;
	}
}
