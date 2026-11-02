package com.growit.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger(springdoc) 문서의 기본 정보와 인증 방식을 정의한다.
 * <p>{@code app.swagger.enabled}가 false면 springdoc 자체가 비활성화되므로,
 * 이 설정은 켜져 있을 때만 의미가 있다. 문서 주소가 인증 없이 열리는지는
 * {@code auth.config.SecurityConfig}가 결정한다.
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	/** 모든 API에 "Authorization: Bearer {토큰}" 입력란을 기본으로 붙인 Swagger 문서를 만든다. */
	@Bean
	public OpenAPI growitOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Growit API")
						.description("활동 기록과 AI 성장 코칭을 위한 Growit 백엔드 API")
						.version("v1"))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
	}
}
