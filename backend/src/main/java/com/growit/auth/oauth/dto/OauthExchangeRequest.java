package com.growit.auth.oauth.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthExchangeRequest(@NotBlank(message = "code를 입력해 주세요") String code) {
}
