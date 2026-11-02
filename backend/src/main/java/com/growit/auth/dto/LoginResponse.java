package com.growit.auth.dto;

/**
 * 로그인 성공 응답.
 *
 * @param accessToken 이후 요청의 {@code Authorization: Bearer} 헤더에 담을 JWT
 */
public record LoginResponse(String accessToken) {
}
