package com.growit.auth.oauth.client;

import com.growit.auth.oauth.entity.SocialProvider;

/** 카카오·구글 공통 인터페이스. 실제 구현은 서버 간 통신을 하고, 테스트는 가짜 구현으로 바꿔 끼운다. */
public interface SocialOauthClient {

	SocialProvider provider();

	String authorizeUrl(String state, String redirectUri);

	/** 인증 코드를 토큰으로 교환하고 프로필을 받아온다. 실패하면 {@link SocialOauthClientException}. */
	SocialProfile exchangeCodeForProfile(String code, String redirectUri);
}
