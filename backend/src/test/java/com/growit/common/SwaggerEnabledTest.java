package com.growit.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SWAGGER_ENABLED(app.swagger.enabled)가 true면 Swagger 화면·api-docs는 인증 없이 열리고,
 * 데이터 API는 여전히 토큰이 필요하다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.swagger.enabled=true")
class SwaggerEnabledTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocs_areAccessibleWithoutToken() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void swaggerUi_isAccessibleWithoutToken() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@Test
	void hobbies_stillRequireToken() throws Exception {
		mockMvc.perform(get("/api/hobbies")).andExpect(status().isUnauthorized());
	}
}
