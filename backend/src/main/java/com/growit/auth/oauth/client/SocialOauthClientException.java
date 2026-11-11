package com.growit.auth.oauth.client;

/** 제공자와의 통신(코드 교환, 프로필 조회)이 실패했을 때. */
public class SocialOauthClientException extends RuntimeException {

	public SocialOauthClientException(String message) {
		super(message);
	}

	public SocialOauthClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
