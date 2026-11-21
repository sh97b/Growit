package com.growit.roadmap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RoadmapItemCreateRequest(
		@NotBlank(message = "항목을 입력해 주세요") @Size(max = 60, message = "항목은 60자 이하로 입력해 주세요") String text) {
}
