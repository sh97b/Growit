package com.growit.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
	void preflight_allowedOrigin_putMethod_returnsOkWithAllowedMethodHeader() throws Exception {
		mockMvc.perform(options("/api/records/1/analysis/feedback")
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "PUT")
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
				.andExpect(result -> assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods"))
						.contains("PUT"));
	}

	@Test
	void preflight_disallowedOrigin_putMethod_isRejected() throws Exception {
		mockMvc.perform(options("/api/records/1/analysis/feedback")
						.header("Origin", DISALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", "PUT")
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isForbidden());
	}

	@Test
	void get_usersMe_withoutToken_stillReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/users/me").header("Origin", ALLOWED_ORIGIN))
				.andExpect(status().isUnauthorized());
	}

	/**
	 * 컨트롤러의 모든 매핑(@GetMapping 등)을 여기에 나열해 둔다. 새 API가 새 HTTP 메서드를 쓰면
	 * 이 목록에 추가해야 사전 확인 누락을 테스트가 바로 잡아낸다.
	 */
	static Stream<Arguments> allControllerMappings() {
		return Stream.of(
				Arguments.of("/api/hobbies", "GET"),
				Arguments.of("/api/hobbies", "POST"),
				Arguments.of("/api/hobbies/1", "PATCH"),
				Arguments.of("/api/hobbies/1", "DELETE"),
				Arguments.of("/api/records", "GET"),
				Arguments.of("/api/records/1", "GET"),
				Arguments.of("/api/records", "POST"),
				Arguments.of("/api/records/1", "PATCH"),
				Arguments.of("/api/records/1", "DELETE"),
				Arguments.of("/api/records/1/analysis", "POST"),
				Arguments.of("/api/records/1/analysis/feedback", "PUT"),
				Arguments.of("/api/records/1/analysis/tags", "PATCH"),
				Arguments.of("/api/health", "GET"),
				Arguments.of("/api/users/me", "GET"),
				Arguments.of("/api/users/me", "PATCH"),
				Arguments.of("/api/users/me/password", "PATCH"),
				Arguments.of("/api/users/me", "DELETE"),
				Arguments.of("/api/auth/signup", "POST"),
				Arguments.of("/api/auth/login", "POST"),
				Arguments.of("/api/stats/summary", "GET"),
				Arguments.of("/api/stats/activity-map", "GET"),
				Arguments.of("/api/stats/weeks", "GET"),
				Arguments.of("/api/stats/weeks/2026-11-02", "GET"),
				Arguments.of("/api/ai/data", "DELETE"),
				Arguments.of("/api/ai/usage", "GET"),
				Arguments.of("/api/ai/compare", "POST"),
				Arguments.of("/api/ai/coach", "POST"),
				Arguments.of("/api/ai/weekly", "POST"),
				Arguments.of("/api/ai/metrics", "POST"),
				Arguments.of("/api/ai/results/1", "GET"),
				Arguments.of("/api/ai/results/1/feedback", "PUT"),
				Arguments.of("/api/ai/results/1/reaction", "PUT"),
				Arguments.of("/api/ai/results/1/text", "PATCH"),
				Arguments.of("/api/ai/results", "GET"),
				Arguments.of("/api/ai/results/latest", "GET"),
				Arguments.of("/api/ai/results/1/simplify", "POST"));
	}

	@ParameterizedTest(name = "{1} {0} 사전 확인 통과")
	@MethodSource("allControllerMappings")
	void preflight_everyControllerMapping_passesAndAllowsItsMethod(String path, String method) throws Exception {
		MvcResult result = mockMvc.perform(options(path)
						.header("Origin", ALLOWED_ORIGIN)
						.header("Access-Control-Request-Method", method)
						.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andReturn();

		String allowedMethods = result.getResponse().getHeader("Access-Control-Allow-Methods");
		assertThat(allowedMethods).isNotNull();
		List<String> methods = List.of(allowedMethods.split(","));
		assertThat(methods).contains(method);
	}
}
