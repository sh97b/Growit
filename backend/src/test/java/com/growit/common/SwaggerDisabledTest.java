package com.growit.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SWAGGER_ENABLED 기본값(false)에서 Swagger 주소는 404이고, 데이터 API는 여전히 토큰이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDisabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocs_areNotFound() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUi_isNotFound() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

	@Test
	void hobbies_stillRequireToken() throws Exception {
		mockMvc.perform(get("/api/hobbies")).andExpect(status().isUnauthorized());
	}
}
