package com.growit.auth.oauth.service;

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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오·구글 로그인 코드 흐름을 묶는다. state 발급·검증, 제공자 교환, 사용자 찾기·만들기,
 * 1회용 로그인 코드 발급·소비, 최종 JWT 발급까지 전부 여기서 처리한다.
 */
@Service
public class OauthService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 30;
	private static final String DEFAULT_DISPLAY_NAME = "Growit 회원";

	private final Map<SocialProvider, SocialOauthClient> clientsByProvider = new EnumMap<>(SocialProvider.class);
	private final OauthProperties properties;
	private final OauthStateService stateService;
	private final OauthLoginCodeStore loginCodeStore;
	private final OauthExchangeRateLimiter exchangeRateLimiter;
	private final SocialAccountRepository socialAccountRepository;
	private final UserRepository userRepository;
	private final JwtTokenProvider jwtTokenProvider;

	public OauthService(List<SocialOauthClient> clients, OauthProperties properties, OauthStateService stateService,
			OauthLoginCodeStore loginCodeStore, OauthExchangeRateLimiter exchangeRateLimiter,
			SocialAccountRepository socialAccountRepository, UserRepository userRepository,
			JwtTokenProvider jwtTokenProvider) {
		for (SocialOauthClient client : clients) {
			clientsByProvider.put(client.provider(), client);
		}
		this.properties = properties;
		this.stateService = stateService;
		this.loginCodeStore = loginCodeStore;
		this.exchangeRateLimiter = exchangeRateLimiter;
		this.socialAccountRepository = socialAccountRepository;
		this.userRepository = userRepository;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	public List<OauthProviderStatus> providers() {
		return List.of(
				new OauthProviderStatus(SocialProvider.KAKAO.name(), isEnabled(SocialProvider.KAKAO)),
				new OauthProviderStatus(SocialProvider.GOOGLE.name(), isEnabled(SocialProvider.GOOGLE)));
	}

	public boolean isEnabled(SocialProvider provider) {
		return switch (provider) {
			case KAKAO -> properties.getKakao().isEnabled();
			case GOOGLE -> properties.getGoogle().isEnabled();
		};
	}

	public SocialProvider parseProvider(String raw) {
		try {
			return SocialProvider.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "지원하지 않는 로그인 방식이에요");
		}
	}

	public String redirectUriFor(SocialProvider provider) {
		return properties.getRedirectBaseUrl() + "/api/auth/oauth/" + provider.name().toLowerCase() + "/callback";
	}

	public String frontendBaseUrl() {
		return properties.getFrontendBaseUrl();
	}

	public String issueState() {
		return stateService.issue();
	}

	public boolean verifyState(String cookieValue, String queryState) {
		return stateService.verify(cookieValue, queryState);
	}

	public String authorizeUrl(SocialProvider provider, String state) {
		return clientOf(provider).authorizeUrl(state, redirectUriFor(provider));
	}

	/** 코드 교환·사용자 찾기(없으면 만들기)까지 한 트랜잭션으로 처리하고, 1회용 로그인 코드를 발급한다. */
	@Transactional
	public String handleCallback(SocialProvider provider, String code) {
		SocialProfile profile;
		try {
			profile = clientOf(provider).exchangeCodeForProfile(code, redirectUriFor(provider));
		} catch (SocialOauthClientException e) {
			throw new ApiException(ErrorCode.OAUTH_CODE_INVALID, "로그인에 실패했어요");
		}

		SocialAccount account = socialAccountRepository
				.findByProviderAndProviderUserId(provider, profile.providerUserId())
				.orElse(null);

		boolean isNewUser = account == null;
		Long userId;
		if (account != null) {
			userId = account.getUserId();
		} else {
			User user = User.forSocialSignup(cleanDisplayName(profile.displayName()));
			userId = userRepository.save(user).getId();
			socialAccountRepository.save(new SocialAccount(userId, provider, profile.providerUserId()));
		}

		return loginCodeStore.issue(userId, isNewUser);
	}

	public OauthExchangeResponse exchange(String code, String clientIp) {
		exchangeRateLimiter.checkAllowed(clientIp);
		OauthLoginCodeStore.Entry entry = loginCodeStore.consume(code);
		if (entry == null) {
			throw new ApiException(ErrorCode.OAUTH_CODE_INVALID, "로그인 코드가 만료됐거나 이미 썼어요");
		}
		User user = userRepository.findById(entry.userId())
				.orElseThrow(() -> new ApiException(ErrorCode.OAUTH_CODE_INVALID, "사용자를 찾을 수 없어요"));
		String accessToken = jwtTokenProvider.createToken(user.getId(), user.getTokenVersion());
		return new OauthExchangeResponse(accessToken, entry.isNewUser());
	}

	private SocialOauthClient clientOf(SocialProvider provider) {
		SocialOauthClient client = clientsByProvider.get(provider);
		if (client == null || !isEnabled(provider)) {
			throw new ApiException(ErrorCode.NOT_FOUND, "꺼져 있는 로그인 방식이에요");
		}
		return client;
	}

	/** 공백·제어문자를 지우고 30자로 자른다. 비어 있으면 기본 이름을 쓴다. */
	private String cleanDisplayName(String rawDisplayName) {
		if (rawDisplayName == null) return DEFAULT_DISPLAY_NAME;
		String cleaned = rawDisplayName.replaceAll("\\p{Cntrl}", "").trim();
		if (cleaned.isEmpty()) return DEFAULT_DISPLAY_NAME;
		return cleaned.length() > DISPLAY_NAME_MAX_LENGTH ? cleaned.substring(0, DISPLAY_NAME_MAX_LENGTH) : cleaned;
	}
}
