package com.geupjido.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Geupjido API",
                version = "v1",
                description = "수도권 아파트 급지 서비스 백엔드 API"
        ),
        tags = {
                @Tag(name = "지도", description = "지도·검색·권역 상세·상태 카드 조회"),
                @Tag(name = "월드컵", description = "권역 선택·게임·최근 선택 조회"),
                @Tag(name = "랭킹", description = "현재 급지 기준의 권역·지역별 순위와 변동 조회")
        }
)
public class OpenApiConfig {
}
