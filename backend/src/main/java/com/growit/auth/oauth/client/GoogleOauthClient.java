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

/** 구글 이메일은 받아도 저장하지 않는다(이메일이 같은 기존 계정과 자동으로 합치지 않기 위해). */
@Component
public class GoogleOauthClient implements SocialOauthClient {

	private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String USER_URL = "https://openidconnect.googleapis.com/v1/userinfo";

	private final OauthProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient = RestClient.create();

	public GoogleOauthClient(OauthProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public SocialProvider provider() {
		return SocialProvider.GOOGLE;
	}

	@Override
	public String authorizeUrl(String state, String redirectUri) {
		String clientId = properties.getGoogle().getClientId();
		return AUTHORIZE_URL
				+ "?response_type=code"
				+ "&client_id=" + encode(clientId)
				+ "&redirect_uri=" + encode(redirectUri)
				+ "&scope=" + encode("openid email profile")
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
		form.add("client_id", properties.getGoogle().getClientId());
		form.add("client_secret", properties.getGoogle().getClientSecret());
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
			if (accessToken == null) throw new SocialOauthClientException("구글 토큰 응답에 access_token이 없어요");
			return accessToken.asText();
		} catch (SocialOauthClientException e) {
			throw e;
		} catch (Exception e) {
			throw new SocialOauthClientException("구글 토큰 교환에 실패했어요", e);
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
			JsonNode subNode = node.get("sub");
			if (subNode == null) throw new SocialOauthClientException("구글 사용자 정보에 sub가 없어요");
			String providerUserId = subNode.asText();
			String name = node.path("name").asText(null);
			return new SocialProfile(providerUserId, name);
		} catch (SocialOauthClientException e) {
			throw e;
		} catch (Exception e) {
			throw new SocialOauthClientException("구글 사용자 정보 조회에 실패했어요", e);
		}
	}

	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
