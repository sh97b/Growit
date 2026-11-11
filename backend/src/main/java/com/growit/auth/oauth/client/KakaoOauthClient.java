package com.growit.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.auth.oauth.config.OauthProperties;
import com.growit.auth.oauth.entity.SocialProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 동의항목은 닉네임만 쓴다(scope를 지정하지 않음 — 카카오 앱의 기본 동의항목이 프로필만이어야 한다).
 * 이메일은 요청도 저장도 하지 않는다.
 */
@Component
public class KakaoOauthClient implements SocialOauthClient {

	private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
	private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
	private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

	private final OauthProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	public KakaoOauthClient(OauthProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.KAKAO;
	}

	@Override
	public String authorizeUrl(String state, String redirectUri) {
		String clientId = properties.getKakao().getClientId();
		return AUTHORIZE_URL
				+ "?response_type=code"
				+ "&client_id=" + encode(clientId)
				+ "&redirect_uri=" + encode(redirectUri)
				+ "&state=" + encode(state);
	}

	@Override
	public SocialProfile exchangeCodeForProfile(String code, String redirectUri) {
		String accessToken = exchangeToken(code, redirectUri);
		return fetchProfile(accessToken);
	}

	private String exchangeToken(String code, String redirectUri) {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "authorization_code");
		form.add("client_id", properties.getKakao().getClientId());
		form.add("client_secret", properties.getKakao().getClientSecret());
		form.add("redirect_uri", redirectUri);
		form.add("code", code);

		try {
			String raw = restClient.post()
					.uri(TOKEN_URL)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(form)
					.retrieve()
					.body(String.class);
			JsonNode node = objectMapper.readTree(raw);
			JsonNode accessToken = node.get("access_token");
			if (accessToken == null) throw new SocialOauthClientException("카카오 토큰 응답에 access_token이 없어요");
			return accessToken.asText();
		} catch (SocialOauthClientException e) {
			throw e;
		} catch (Exception e) {
			throw new SocialOauthClientException("카카오 토큰 교환에 실패했어요", e);
		}
	}

	private SocialProfile fetchProfile(String accessToken) {
		try {
			String raw = restClient.get()
					.uri(USER_URL)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
					.retrieve()
					.body(String.class);
			JsonNode node = objectMapper.readTree(raw);
			JsonNode idNode = node.get("id");
			if (idNode == null) throw new SocialOauthClientException("카카오 사용자 정보에 id가 없어요");
			String providerUserId = idNode.asText();
			String nickname = node.path("properties").path("nickname").asText(null);
			return new SocialProfile(providerUserId, nickname);
		} catch (SocialOauthClientException e) {
			throw e;
		} catch (Exception e) {
			throw new SocialOauthClientException("카카오 사용자 정보 조회에 실패했어요", e);
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
