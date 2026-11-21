package com.growit.roadmap.dto.request;

import jakarta.validation.constraints.Size;

/** move는 "UP" 또는 "DOWN"(같은 단계 안에서 이웃한 항목과 자리 바꾸기)이다. 보낸 칸만 바뀐다. */
public record RoadmapItemUpdateRequest(
		@Size(max = 60, message = "항목은 60자 이하로 입력해 주세요") String text,
		String move) {
}
