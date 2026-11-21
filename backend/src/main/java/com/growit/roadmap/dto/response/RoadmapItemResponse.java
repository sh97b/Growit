package com.growit.roadmap.dto.response;

import java.time.LocalDateTime;

public record RoadmapItemResponse(Long id, int position, String text, boolean done, LocalDateTime doneAt,
		String source) {
}
