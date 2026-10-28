package com.growit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
		@Schema(example = "growit_user") @NotBlank(message = "아이디를 입력해 주세요") String username,
		@Schema(example = "password1234") @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
}
