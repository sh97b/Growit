package com.growit.roadmap.dto.request;

import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * move는 "UP" 또는 "DOWN"(이웃한 단계와 자리 바꾸기)이다. 보낸 칸만 바뀐다. periodHint를 null로 보내면 비우고
 * 20자 제한은 서비스가 확인한다(JsonNullable 안쪽은 검증기가 보지 않는다).
 */
public record RoadmapStepUpdateRequest(
		@Size(max = 40, message = "단계 제목은 40자 이하로 입력해 주세요") String title,
		JsonNullable<String> periodHint,
		String move) {
}
