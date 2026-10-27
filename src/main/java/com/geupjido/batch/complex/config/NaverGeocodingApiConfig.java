package com.geupjido.batch.complex.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * 네이버 Maps Geocoding API 호출에 사용할 HTTP 클라이언트를 구성한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = "app.external.geocoding",
	name = "enabled",
	havingValue = "true"
)
public class NaverGeocodingApiConfig {

	@Bean
	public RestClient naverGeocodingRestClient(
		RestClient.Builder builder,
		NaverGeocodingProperties properties
	) {
		Assert.hasText(properties.baseUrl(), "Geocoding API 주소가 필요합니다.");
		Assert.hasText(properties.clientId(), "네이버 Maps Client ID가 필요합니다.");
		Assert.hasText(properties.clientSecret(), "네이버 Maps Client Secret이 필요합니다.");

		JdkClientHttpRequestFactory requestFactory =
			new JdkClientHttpRequestFactory(
				HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(10))
					.build()
			);
		requestFactory.setReadTimeout(Duration.ofSeconds(30));

		return builder
			.baseUrl(properties.baseUrl())
			.defaultHeader("x-ncp-apigw-api-key-id", properties.clientId())
			.defaultHeader("x-ncp-apigw-api-key", properties.clientSecret())
			.defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
			.requestFactory(requestFactory)
			.build();
	}
}
