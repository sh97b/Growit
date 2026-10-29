package com.growit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로그인(JWT) 적용 후 전체 보안 동작을 실제 HTTP 흐름으로 확인한다:
 * 토큰 없음/위조/만료 시 401, 계정 2개 사이의 데이터 분리.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${app.jwt.secret}")
	private String jwtSecret;

	private String signupAndLogin(String localPart) throws Exception {
		String email = localPart + "@example.com";
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
				.andExpect(status().isOk())
				.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return body.get("accessToken").asText();
	}

	@Test
	void health_doesNotNeedToken() throws Exception {
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void hobbies_withoutToken_returnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/hobbies"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void hobbies_withTamperedToken_returnsUnauthorized() throws Exception {
		String token = signupAndLogin("tamper_user");
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + tampered))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void hobbies_withExpiredToken_returnsUnauthorized() throws Exception {
		signupAndLogin("expired_user");
		JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(jwtSecret, -1);
		String expiredToken = expiredTokenProvider.createToken(1L);

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void usersMe_returnsOwnInfo() throws Exception {
		String token = signupAndLogin("me_user");

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is("me_user@example.com")))
				.andExpect(jsonPath("$.displayName", is("me_user")));
	}

	@Test
	void twoAccounts_cannotSeeOrTouchEachOthersData() throws Exception {
		String aliceToken = signupAndLogin("alice_user");
		String bobToken = signupAndLogin("bob_user");

		MvcResult createHobby = mockMvc.perform(post("/api/hobbies")
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"기타\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long aliceHobbyId = objectMapper.readTree(createHobby.getResponse().getContentAsString()).get("id").asLong();

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

		mockMvc.perform(patch("/api/hobbies/{id}", aliceHobbyId)
						.header("Authorization", "Bearer " + bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"피아노\"}"))
				.andExpect(status().isNotFound());

		MvcResult createRecord = mockMvc.perform(post("/api/records")
						.header("Authorization", "Bearer " + aliceToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"hobbyId\":" + aliceHobbyId
								+ ",\"recordDate\":\"2026-10-28\",\"content\":\"연습했다\"}"))
				.andExpect(status().isCreated())
				.andReturn();
		long aliceRecordId = objectMapper.readTree(createRecord.getResponse().getContentAsString())
				.get("id").asLong();

		mockMvc.perform(get("/api/records").header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCount", is(0)));

		mockMvc.perform(get("/api/records/{id}", aliceRecordId).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/records/{id}", aliceRecordId)
						.header("Authorization", "Bearer " + bobToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"남의 기록 수정\"}"))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/records/{id}", aliceRecordId).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNotFound());

		mockMvc.perform(delete("/api/hobbies/{id}", aliceHobbyId).header("Authorization", "Bearer " + bobToken))
				.andExpect(status().isNotFound());

		assertThat(aliceHobbyId).isPositive();
	}
}
