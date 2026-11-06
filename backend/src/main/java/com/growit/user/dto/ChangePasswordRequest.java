package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
		@Schema(example = "password1234") @NotBlank(message = "현재 비밀번호를 입력해 주세요") String currentPassword,
		@Schema(example = "Gr0wit-Journal9") @NotBlank(message = "새 비밀번호를 입력해 주세요") String newPassword) {
}
