package com.growit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@Schema(example = "grow.user@example.com") @NotBlank(message = "이메일을 입력해 주세요") @Email(
				message = "올바른 이메일 형식으로 입력해 주세요") String email,
		@Schema(example = "password1234") @Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요") String password) {
}
