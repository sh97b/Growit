package com.growit.auth.oauth.dto;

public record OauthExchangeResponse(String accessToken, boolean isNewUser) {
}
