package com.growit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 길이·흔한 비밀번호·이메일 포함 여부 같은 규칙은 {@link com.growit.auth.security.PasswordPolicy}가 따로 검사한다. */
public record SignupRequest(
		@Schema(example = "grow.user@example.com") @NotBlank(message = "이메일을 입력해 주세요") @Email(
				message = "올바른 이메일 형식으로 입력해 주세요") String email,
		@Schema(example = "password1234") @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
}
