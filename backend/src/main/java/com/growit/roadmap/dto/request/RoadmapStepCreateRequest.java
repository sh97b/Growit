package com.growit.roadmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoadmapStepCreateRequest(
		@NotBlank(message = "단계 제목을 입력해 주세요") @Size(max = 40, message = "단계 제목은 40자 이하로 입력해 주세요") String title,
		@Size(max = 20, message = "기간 힌트는 20자 이하로 입력해 주세요") String periodHint) {
}
