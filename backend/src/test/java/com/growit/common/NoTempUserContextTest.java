package com.growit.common;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * APP_TEMP_USER_ENABLED가 없거나 false인 서버(prod) 환경에서
 * 애플리케이션이 정상적으로 뜨고, 사용자 식별이 필요한 API는 401로 응답하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = "app.temp-user.enabled=false")
class NoTempUserContextTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void health_doesNotNeedCurrentUser() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk());
	}

	@Test
	void hobbies_withoutLogin_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/hobbies"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void records_withoutLogin_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/records"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}
}
