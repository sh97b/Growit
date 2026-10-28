package com.growit.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

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
