package com.growit.roadmap.dto.response;

import java.time.LocalDateTime;

/** blockCount는 그 항목에 붙은 블록 수(목록의 작은 배지용). */
public record RoadmapItemResponse(Long id, int position, String text, boolean done, LocalDateTime doneAt,
		String source, int blockCount) {
}
