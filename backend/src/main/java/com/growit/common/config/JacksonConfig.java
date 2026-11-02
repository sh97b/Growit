package com.growit.common.config;

import org.openapitools.jackson.nullable.JsonNullableModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson이 {@link org.openapitools.jackson.nullable.JsonNullable} 필드를 다룰 수 있게 등록한다.
 * <p>PATCH 요청에서 "칸을 보내지 않음(그대로 둠)"과 "null로 보냄(비움)"을 구분해야 하는
 * 요청 DTO(예: 기록 수정, 활동 분야 수정)가 이 모듈에 의존한다.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public JsonNullableModule jsonNullableModule() {
		return new JsonNullableModule();
	}
}
