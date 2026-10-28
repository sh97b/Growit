package com.growit.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@Schema(example = "growit_user") @Pattern(regexp = "^[a-z0-9_]{4,30}$",
				message = "아이디는 영문 소문자·숫자·밑줄로 4~30자여야 해요") String username,
		@Schema(example = "password1234") @Size(min = 8, max = 72, message = "비밀번호는 8~72자로 입력해 주세요") String password) {
}
