package com.geupjido.match.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WorldcupCorsConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WorldcupCorsConfig(@Value("${app.worldcup-cors.allowed-origins:}") String allowedOrigins) {
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
	}
}
