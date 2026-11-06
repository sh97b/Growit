package com.growit.auth.config;

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
import com.growit.auth.security.JwtTokenProvider;
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
						.content("{\"email\":\"" + email + "\",\"password\":\"Gr0wit-Journal9\"}"))
				.andExpect(status().isCreated());

		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"Gr0wit-Journal9\"}"))
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

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + tamperSignature(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void hobbies_withTamperedToken_returnsUnauthorized_repeated() throws Exception {
		// 서명 끝 글자를 바꾸던 예전 방식은 base64url의 사용되지 않는 비트 때문에 같은 서명으로
		// 디코딩되는 경우가 있어 간헐적으로 실패했다. 20회 반복해 안정적으로 401이 나는지 확인한다.
		for (int i = 0; i < 20; i++) {
			String token = signupAndLogin("tamper_repeat_user_" + i);
			mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + tamperSignature(token)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
		}
	}

	/** 서명 세그먼트의 첫 글자를 바꾼다. 첫 글자는 base64url에서 패딩·미사용 비트가 없어 항상 실제로 다른 바이트로 디코딩된다. */
	private static String tamperSignature(String token) {
		int lastDot = token.lastIndexOf('.');
		String head = token.substring(0, lastDot + 1);
		String signature = token.substring(lastDot + 1);
		char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
		return head + replacement + signature.substring(1);
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
	void hobbies_withTokenHavingNoVersionClaim_stillAuthenticates() throws Exception {
		// 배포 전에 발급된(tv 클레임이 없는) 토큰도 DB의 token_version이 기본값 0이면 그대로 통한다.
		String token = signupAndLogin("legacy_token_user");
		long userId = objectMapper.readTree(mockMvc.perform(get("/api/users/me")
						.header("Authorization", "Bearer " + token))
				.andReturn().getResponse().getContentAsString()).get("id").asLong();
		String legacyToken = new JwtTokenProvider(jwtSecret, 7).createToken(userId);

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + legacyToken))
				.andExpect(status().isOk());
	}

	@Test
	void changePassword_invalidatesOldTokenButNewTokenKeepsWorking() throws Exception {
		String oldToken = signupAndLogin("change_pw_user");

		MvcResult changeResult = mockMvc.perform(patch("/api/users/me/password")
						.header("Authorization", "Bearer " + oldToken)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"Gr0wit-Journal9\",\"newPassword\":\"Another-Secure7\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String newToken = objectMapper.readTree(changeResult.getResponse().getContentAsString())
				.get("accessToken").asText();

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + oldToken))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + newToken))
				.andExpect(status().isOk());
	}

	@Test
	void changePassword_wrongCurrentPassword_returnsUnauthorized() throws Exception {
		String token = signupAndLogin("change_pw_wrong_user");

		mockMvc.perform(patch("/api/users/me/password")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"Another-Secure7\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
	}

	@Test
	void deleteAccount_thenSameTokenReturnsUnauthorizedAndEmailCanSignUpAgain() throws Exception {
		String email = "delete_account_user@example.com";
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"Gr0wit-Journal9\"}"))
				.andExpect(status().isCreated());
		MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"Gr0wit-Journal9\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
				.get("accessToken").asText();

		mockMvc.perform(delete("/api/users/me")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"Gr0wit-Journal9\"}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"Gr0wit-Journal9\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void deleteAccount_wrongPassword_returnsUnauthorized() throws Exception {
		String token = signupAndLogin("delete_wrong_pw_user");

		mockMvc.perform(delete("/api/users/me")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
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
