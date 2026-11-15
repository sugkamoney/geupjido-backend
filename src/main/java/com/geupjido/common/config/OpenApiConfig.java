package com.geupjido.common.config;

import java.util.Set;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
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
	@Bean
	OpenApiCustomizer nullableMapComplexLocation() {
		return openApi -> {
			Schema<?> complex = openApi.getComponents().getSchemas().get("MapComplex");
			if (complex != null) {
				// OpenAPI 3.1에서 nullable 객체 참조는 참조와 null의 합집합으로 표현한다.
				// nullable=true만 붙이면 springdoc이 type:null과 $ref를 함께 내보낸다.
				complex.addProperty("location", new ComposedSchema()
					.addOneOfItem(new Schema<>().$ref("#/components/schemas/Point"))
					.addOneOfItem(new Schema<>().types(Set.of("null")))
					.description("WGS84 단지 위치. 위치 미확인 시 null"));
			}
		};
	}
}
