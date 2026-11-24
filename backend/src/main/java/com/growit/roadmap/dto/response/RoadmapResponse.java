package com.growit.roadmap.dto.response;

import java.time.LocalDate;
import java.util.List;

/** completedStepMessage는 항목을 방금 체크해 그 단계가 모두 끝났을 때만 값이 있다(그 응답에서만 쓰고 저장하지 않는다). */
/** state는 ACTIVE 또는 ARCHIVED(보관된 로드맵은 읽기 전용). */
public record RoadmapResponse(Long id, Long hobbyId, String state, String goalText, LocalDate targetDate,
		List<RoadmapStepResponse> steps, String completedStepMessage) {
}
