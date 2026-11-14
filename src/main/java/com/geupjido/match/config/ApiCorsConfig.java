package com.geupjido.match.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 프런트와 API의 출처가 다를 때 허용할 경로와 HTTP 메서드를 지정한다. */
@Configuration(proxyBeanMethods = false)
public class ApiCorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public ApiCorsConfig(@Value("${app.worldcup-cors.allowed-origins:}") String allowedOrigins) {
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(origin -> !origin.isEmpty())
			.toArray(String[]::new);
		if (Arrays.asList(this.allowedOrigins).contains("*")) {
			throw new IllegalArgumentException("월드컵 쿠키 요청에는 명시적인 허용 출처가 필요합니다.");
		}
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if (allowedOrigins.length == 0) {
			return;
		}
		registry.addMapping("/api/v1/worldcup/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PUT")
			.allowedHeaders("Content-Type")
			.allowCredentials(true)
			.maxAge(3600);
		// 지도 API는 읽기 전용이라 GET만 허용한다. 기존 허용 출처를 재사용한다.
		registry.addMapping("/api/v1/map/**")
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET")
			.allowedHeaders("Content-Type")
			.allowCredentials(true)
			.maxAge(3600);
	}
}
