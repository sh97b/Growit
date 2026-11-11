package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteAccountRequest(
		@Schema(example = "Gr0wit-Journal9") String password,
		@Schema(example = "계정 삭제") String confirmation) {
}
