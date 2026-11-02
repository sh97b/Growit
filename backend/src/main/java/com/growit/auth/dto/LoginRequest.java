package com.growit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * @param email    가입할 때 쓴 이메일. 비교 전에 서버가 소문자로 통일한다
 * @param password 원문 비밀번호. 저장된 해시와 {@code PasswordEncoder}로 비교한다
 */
public record LoginRequest(
		@Schema(example = "grow.user@example.com") @NotBlank(message = "이메일을 입력해 주세요") String email,
		@Schema(example = "password1234") @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
}
