package com.growit.auth.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.auth.oauth.client.SocialOauthClient;
import com.growit.auth.oauth.client.SocialOauthClientException;
import com.growit.auth.oauth.client.SocialProfile;
import com.growit.auth.oauth.config.OauthProperties;
import com.growit.auth.oauth.dto.OauthExchangeResponse;
import com.growit.auth.oauth.dto.OauthProviderStatus;
import com.growit.auth.oauth.entity.SocialAccount;
import com.growit.auth.oauth.entity.SocialProvider;
import com.growit.auth.oauth.repository.SocialAccountRepository;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class OauthServiceTest {

	private static final String TEST_SECRET = "test-only-secret-key-please-replace-32bytes-min";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SocialAccountRepository socialAccountRepository;

	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 7);

	/** 실제 HTTP 통신 없이 미리 정한 프로필을 돌려주거나(혹은 실패를 던지거나) 하는 가짜 제공자. */
	private static final class FakeSocialOauthClient implements SocialOauthClient {
		private final SocialProvider provider;
		private SocialProfile profileToReturn;
		private boolean shouldFail;

		private FakeSocialOauthClient(SocialProvider provider) {
			this.provider = provider;
		}

		@Override
		public SocialProvider provider() {
			return provider;
		}

		@Override
		public String authorizeUrl(String state, String redirectUri) {
			return "https://provider.example/authorize?state=" + state;
		}

		@Override
		public SocialProfile exchangeCodeForProfile(String code, String redirectUri) {
			if (shouldFail) throw new SocialOauthClientException("가짜 제공자 오류");
			return profileToReturn;
		}
	}

	private OauthProperties enabledProperties() {
		OauthProperties properties = new OauthProperties();
		properties.getKakao().setClientId("test-kakao-id");
		properties.getKakao().setClientSecret("test-kakao-secret");
		properties.getGoogle().setClientId("test-google-id");
		properties.getGoogle().setClientSecret("test-google-secret");
		return properties;
	}

	private OauthService service(OauthProperties properties, FakeSocialOauthClient kakaoClient,
			FakeSocialOauthClient googleClient, Clock clock) {
		return new OauthService(List.of(kakaoClient, googleClient), properties,
				new OauthStateService(TEST_SECRET, clock), new OauthLoginCodeStore(clock),
				new OauthExchangeRateLimiter(clock), socialAccountRepository, userRepository, jwtTokenProvider);
	}

	private FakeSocialOauthClient kakao() {
		return new FakeSocialOauthClient(SocialProvider.KAKAO);
	}

	private FakeSocialOauthClient google() {
		return new FakeSocialOauthClient(SocialProvider.GOOGLE);
	}

	@Test
	void providers_bothKeysMissing_bothDisabled() {
		OauthService service = service(new OauthProperties(), kakao(), google(), Clock.systemUTC());

		List<OauthProviderStatus> result = service.providers();

		assertThat(result).extracting(OauthProviderStatus::enabled).containsExactly(false, false);
	}

	@Test
	void providers_onlyKakaoKeysPresent_onlyKakaoEnabled() {
		OauthProperties properties = new OauthProperties();
		properties.getKakao().setClientId("id");
		properties.getKakao().setClientSecret("secret");
		OauthService service = service(properties, kakao(), google(), Clock.systemUTC());

		List<OauthProviderStatus> result = service.providers();

		assertThat(result).containsExactlyInAnyOrder(
				new OauthProviderStatus("KAKAO", true),
				new OauthProviderStatus("GOOGLE", false));
	}

	@Test
	void handleCallback_disabledProvider_throwsNotFound() {
		FakeSocialOauthClient kakaoClient = kakao();
		OauthService service = service(new OauthProperties(), kakaoClient, google(), Clock.systemUTC());

		assertThatThrownBy(() -> service.handleCallback(SocialProvider.KAKAO, "any-code"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	void handleCallback_newProviderUser_createsUserAndSocialAccount() {
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.profileToReturn = new SocialProfile("kakao-id-1", "홍길동");
		OauthService service = service(enabledProperties(), kakaoClient, google(), Clock.systemUTC());

		String loginCode = service.handleCallback(SocialProvider.KAKAO, "auth-code");

		assertThat(loginCode).isNotBlank();
		SocialAccount saved = socialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-id-1")
				.orElseThrow();
		User user = userRepository.findById(saved.getUserId()).orElseThrow();
		assertThat(user.getEmail()).isNull();
		assertThat(user.hasPassword()).isFalse();
		assertThat(user.getDisplayName()).isEqualTo("홍길동");
	}

	@Test
	void handleCallback_existingProviderUser_reusesSameUserAndMarksNotNew() {
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.profileToReturn = new SocialProfile("kakao-id-2", "첫 로그인");
		OauthService service = service(enabledProperties(), kakaoClient, google(), Clock.systemUTC());
		String firstCode = service.handleCallback(SocialProvider.KAKAO, "code-1");
		OauthExchangeResponse firstResponse = service.exchange(firstCode, "127.0.0.1");

		kakaoClient.profileToReturn = new SocialProfile("kakao-id-2", "두 번째 로그인 닉네임");
		String secondCode = service.handleCallback(SocialProvider.KAKAO, "code-2");
		OauthExchangeResponse secondResponse = service.exchange(secondCode, "127.0.0.1");

		assertThat(firstResponse.isNewUser()).isTrue();
		assertThat(secondResponse.isNewUser()).isFalse();
		assertThat(socialAccountRepository.findAll()).hasSize(1);
	}

	@Test
	void handleCallback_providerError_throwsOauthCodeInvalid() {
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.shouldFail = true;
		OauthService service = service(enabledProperties(), kakaoClient, google(), Clock.systemUTC());

		assertThatThrownBy(() -> service.handleCallback(SocialProvider.KAKAO, "bad-code"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OAUTH_CODE_INVALID));
	}

	@Test
	void handleCallback_doesNotMergeWithExistingEmailAccount() {
		// 이메일을 아예 받지도 저장하지도 않으므로, 이메일이 같은 기존 계정이 있어도 합쳐지지 않는다
		// (애초에 비교할 이메일 자체가 없다 — 설계로 막혀 있음을 확인).
		userRepository.save(new User("existing@example.com", "hashed", "기존 회원"));
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.profileToReturn = new SocialProfile("kakao-id-3", "기존 회원");
		OauthService service = service(enabledProperties(), kakaoClient, google(), Clock.systemUTC());

		service.handleCallback(SocialProvider.KAKAO, "code");

		assertThat(userRepository.count()).isEqualTo(2);
	}

	@Test
	void exchange_codeUsedTwice_secondAttemptThrowsOauthCodeInvalid() {
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.profileToReturn = new SocialProfile("kakao-id-4", "사용자");
		OauthService service = service(enabledProperties(), kakaoClient, google(), Clock.systemUTC());
		String code = service.handleCallback(SocialProvider.KAKAO, "code");

		service.exchange(code, "127.0.0.1");

		assertThatThrownBy(() -> service.exchange(code, "127.0.0.1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OAUTH_CODE_INVALID));
	}

	@Test
	void exchange_expiredCode_throwsOauthCodeInvalid() {
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(base);
		Clock movableClock = new Clock() {
			@Override
			public java.time.ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return now.get();
			}
		};
		FakeSocialOauthClient kakaoClient = kakao();
		kakaoClient.profileToReturn = new SocialProfile("kakao-id-5", "사용자");
		OauthService service = service(enabledProperties(), kakaoClient, google(), movableClock);
		String code = service.handleCallback(SocialProvider.KAKAO, "code");

		now.set(base.plusSeconds(61));

		assertThatThrownBy(() -> service.exchange(code, "127.0.0.1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OAUTH_CODE_INVALID));
	}

	@Test
	void exchange_invalidCode_throwsOauthCodeInvalid() {
		OauthService service = service(enabledProperties(), kakao(), google(), Clock.systemUTC());

		assertThatThrownBy(() -> service.exchange("never-issued-code", "127.0.0.1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.OAUTH_CODE_INVALID));
	}

	@Test
	void verifyState_cookieMismatch_returnsFalse() {
		OauthService service = service(enabledProperties(), kakao(), google(), Clock.systemUTC());
		String state = service.issueState();

		assertThat(service.verifyState(state, "다른-state")).isFalse();
		assertThat(service.verifyState(null, state)).isFalse();
	}

	@Test
	void verifyState_validRoundTrip_returnsTrue() {
		OauthService service = service(enabledProperties(), kakao(), google(), Clock.systemUTC());
		String state = service.issueState();

		assertThat(service.verifyState(state, state)).isTrue();
	}
}
