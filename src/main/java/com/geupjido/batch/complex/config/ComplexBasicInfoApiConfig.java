package com.geupjido.batch.complex.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * 공동주택 단지 기본정보 API 호출에 사용하는 HTTP 클라이언트를 구성한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
	prefix = "app.external.complex-basic-info",
	name = "enabled",
	havingValue = "true"
)
public class ComplexBasicInfoApiConfig {

	@Bean
	public RestClient complexBasicInfoRestClient(
		RestClient.Builder builder,
		ComplexBasicInfoApiProperties properties
	) {
		Assert.hasText(
			properties.baseUrl(),
			"공동주택 단지 기본정보 API End Point가 필요합니다."
		);
		Assert.hasText(
			properties.serviceKey(),
			"공공데이터포털 일반 인증키가 필요합니다."
		);

		return builder
			.baseUrl(properties.baseUrl())
			.build();
	}
}
