package com.growit.hobby.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HobbyCreateRequest(
		@NotBlank(message = "분야 이름을 입력해 주세요") @Size(max = 50, message = "분야 이름은 50자 이하로 입력해 주세요") String name,
		@Size(max = 200, message = "목표는 200자 이하로 입력해 주세요") String goal) {
}
