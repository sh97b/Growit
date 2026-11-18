package com.growit.dot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DotGoalStartRequest(
		@Schema(example = "dumbbell") @NotBlank(message = "그림을 골라 주세요") String presetKey,
		@Schema(example = "30") @NotNull(message = "목표 일수를 입력해 주세요") Integer goalDays) {
}
