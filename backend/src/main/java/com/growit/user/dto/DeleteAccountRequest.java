package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
		@Schema(example = "Gr0wit-Journal9") @NotBlank(message = "비밀번호를 입력해 주세요") String password) {
}
