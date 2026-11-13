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
import com.growit.auth.oauth.entity.SocialAccount;
import com.growit.auth.oauth.entity.SocialProvider;
import com.growit.auth.oauth.repository.SocialAccountRepository;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.common.config.CorsIntegrationTest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 로그인(JWT) 적용 후 전체 보안 동작을 실제 HTTP 흐름으로 확인한다:
 * 토큰 없음/위조/만료 시 401, 계정 2개 사이의 데이터 분리.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

	/** 토큰 없이도 통과해야 하는 경로 — {@link com.growit.auth.config.SecurityConfig}의 공개 경로 목록과 맞춘다. */
	private static final Set<String> PUBLIC_MAPPINGS = Set.of(
			"GET /api/health",
			"POST /api/auth/signup",
			"POST /api/auth/login",
			"GET /api/auth/oauth/providers",
			"GET /api/auth/oauth/kakao/start",
			"GET /api/auth/oauth/kakao/callback",
			"POST /api/auth/oauth/exchange",
			"GET /api/push/public-key");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

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

	/** 이메일·비밀번호 없는 소셜 전용 계정(email·password_hash NULL)을 만들어 그 계정의 토큰을 돌려준다. */
	private String signupSocialAndLogin(String providerUserId) {
		User user = userRepository.save(new User(null, null, "소셜 회원"));
		socialAccountRepository.save(new SocialAccount(user.getId(), SocialProvider.GOOGLE, providerUserId));
		return jwtTokenProvider.createToken(user.getId(), user.getTokenVersion());
	}

	@Test
	void health_doesNotNeedToken() throws Exception {
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void hobbies_withoutToken_returnsTokenInvalid() throws Exception {
		mockMvc.perform(get("/api/hobbies"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("TOKEN_INVALID")));
	}

	@Test
	void hobbies_withTamperedToken_returnsTokenInvalid() throws Exception {
		String token = signupAndLogin("tamper_user");

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + tamperSignature(token)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("TOKEN_INVALID")));
	}

	@Test
	void hobbies_withTamperedToken_returnsTokenInvalid_repeated() throws Exception {
		// 서명 끝 글자를 바꾸던 예전 방식은 base64url의 사용되지 않는 비트 때문에 같은 서명으로
		// 디코딩되는 경우가 있어 간헐적으로 실패했다. 20회 반복해 안정적으로 401이 나는지 확인한다.
		for (int i = 0; i < 20; i++) {
			String token = signupAndLogin("tamper_repeat_user_" + i);
			mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + tamperSignature(token)))
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code", is("TOKEN_INVALID")));
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
	void hobbies_withExpiredToken_returnsTokenInvalid() throws Exception {
		signupAndLogin("expired_user");
		JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(jwtSecret, -1);
		String expiredToken = expiredTokenProvider.createToken(1L);

		mockMvc.perform(get("/api/hobbies").header("Authorization", "Bearer " + expiredToken))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code", is("TOKEN_INVALID")));
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
	void changePassword_wrongCurrentPassword_returnsPasswordMismatch() throws Exception {
		String token = signupAndLogin("change_pw_wrong_user");

		mockMvc.perform(patch("/api/users/me/password")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"Another-Secure7\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code", is("PASSWORD_MISMATCH")));

		// 틀린 현재 비밀번호는 세션을 끊지 않는다 - 같은 토큰으로 계속 로그인 상태를 유지한다.
		mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
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
	void deleteAccount_wrongPassword_returnsPasswordMismatch() throws Exception {
		String token = signupAndLogin("delete_wrong_pw_user");

		mockMvc.perform(delete("/api/users/me")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"password\":\"wrong-password\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code", is("PASSWORD_MISMATCH")));
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

	@Test
	void pushAndNotificationEndpoints_withValidToken_succeed() throws Exception {
		String token = signupAndLogin("push_flow_user");

		mockMvc.perform(get("/api/push/public-key").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		// p256dh(65바이트, 0x04로 시작)·auth(16바이트) 형식 검증(2026-11-13)을 통과하는 가짜 키.
		mockMvc.perform(post("/api/push/subscriptions")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"endpoint\":\"https://fake-push.example.com/ep1\","
								+ "\"keys\":{\"p256dh\":\"BKAjjhW0MhkfWq6HM-3Q-ngvw9NlZNi69XsoJJCEanbYegIpfLGi2nej-JcZjeeqqe8dQwt9wqdvRa5t4xWHcuk\","
								+ "\"auth\":\"9NN7EXMOwQz0oOzWaIiEfA\"}}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/notifications/settings").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(MockMvcRequestBuilders.put("/api/notifications/settings")
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"enabled\":true,\"timeOfDay\":\"08:00\",\"daysOfWeek\":\"1111111\","
								+ "\"notifyOnCoachArrival\":true}"))
				.andExpect(status().isOk());

		// VAPID 키가 없는 테스트 환경에서는 PUSH_DISABLED(409)이지 TOKEN_INVALID(401)가 아니다.
		mockMvc.perform(post("/api/push/test").header("Authorization", "Bearer " + token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("PUSH_DISABLED")));
	}

	private static Stream<Arguments> nonPublicControllerMappings() {
		return CorsIntegrationTest.allControllerMappings()
				.filter(args -> !PUBLIC_MAPPINGS.contains(args.get()[1] + " " + args.get()[0]));
	}

	/**
	 * 공개 경로를 뺀 모든 API가, 이메일 가입 계정과 소셜 전용 계정(email·password_hash NULL) 둘 다의 유효한
	 * 토큰으로는 401(TOKEN_INVALID)을 돌려주지 않는지 확인한다. 404·400·409 등 다른 상태는 상관없다 —
	 * 여기서 잡으려는 건 "인증은 통과했는가"뿐이다.
	 */
	@ParameterizedTest(name = "{1} {0} - 이메일 계정 토큰으로 401 아님")
	@MethodSource("nonPublicControllerMappings")
	void everyNonPublicMapping_withValidEmailAccountToken_isNotUnauthorized(String path, String method)
			throws Exception {
		String token = signupAndLogin("auth_coverage_email_" + path.hashCode() + method.hashCode());

		mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
	}

	@ParameterizedTest(name = "{1} {0} - 소셜 전용 계정 토큰으로 401 아님")
	@MethodSource("nonPublicControllerMappings")
	void everyNonPublicMapping_withValidSocialAccountToken_isNotUnauthorized(String path, String method)
			throws Exception {
		String token = signupSocialAndLogin("social_coverage_" + path.hashCode() + method.hashCode());

		mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
						.header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
	}
}
