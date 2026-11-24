package com.growit.roadmap.dto.response;

import java.time.LocalDateTime;

/** 보관함 목록의 카드 한 장. archivedAt은 보관한 시각이다. */
public record RoadmapSummaryResponse(Long id, String goalText, LocalDateTime createdAt, LocalDateTime archivedAt,
		int stepCount, int itemCount, int doneCount) {
}
