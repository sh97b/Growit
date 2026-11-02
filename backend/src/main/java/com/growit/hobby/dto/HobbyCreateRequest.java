package com.growit.hobby.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HobbyCreateRequest(
		@Schema(example = "기타") @NotBlank(message = "분야 이름을 입력해 주세요") @Size(max = 50, message = "분야 이름은 50자 이하로 입력해 주세요") String name,
		@Schema(example = "코드 전환 익숙해지기") @Size(max = 200, message = "목표는 200자 이하로 입력해 주세요") String goal) {
}
