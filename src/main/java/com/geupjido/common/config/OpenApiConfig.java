package com.geupjido.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Geupjido API",
                version = "v1",
                description = "수도권 아파트 급지 서비스 백엔드 API"
        )
)
public class OpenApiConfig {
}
