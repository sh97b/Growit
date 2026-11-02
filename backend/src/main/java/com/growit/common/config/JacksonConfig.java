package com.growit.common.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PATCH 요청에서 "칸을 안 보냄(유지)"과 "null로 보냄(비움)"을 구분할 수 있게 한다. */
@Configuration
public class JacksonConfig {

	@Bean
	public JsonNullableModule jsonNullableModule() {
		return new JsonNullableModule();
	}
}
