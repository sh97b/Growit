package com.growit.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS 사전 확인(OPTIONS)이 Spring Security 인증 검사보다 먼저 처리되는지 확인한다.
 * 테스트 프로필의 허용 도메인은 http://localhost:5173 (src/test/resources/application.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsIntegrationTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:5173";
	private static final String DISALLOWED_ORIGIN = "https://evil.example.com";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void preflight_allowedOrigin_usersMe_returnsOkWithCorsHeader() throws Exception {
		mockMvc.perform(options("/api/users/me")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "PATCH")
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
	}

	@Test
	void preflight_allowedOrigin_hobbies_returnsOkWithCorsHeader() throws Exception {
		mockMvc.perform(options("/api/hobbies")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "PATCH")
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
	}

	@Test
	void preflight_disallowedOrigin_isRejected() throws Exception {
		mockMvc.perform(options("/api/users/me")
						.header("Origin", DISALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "PATCH")
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isForbidden());
	}

	@Test
	void get_usersMe_withoutToken_stillReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users/me").header("Origin", ALLOWED_ORIGIN))
				.andExpect(status().isUnauthorized());
	}
}
