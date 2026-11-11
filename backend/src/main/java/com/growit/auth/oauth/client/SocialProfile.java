package com.growit.auth.oauth.client;

/** 제공자에게서 받은 최소 정보. 카카오는 이메일을 요청·저장하지 않고, 구글은 이메일을 받아도 저장하지 않는다. */
public record SocialProfile(String providerUserId, String displayName) {
}
