package com.growit.roadmap.dto.response;

import java.time.LocalDate;
import java.util.List;

public record RoadmapResponse(Long id, Long hobbyId, String goalText, LocalDate targetDate,
		List<RoadmapStepResponse> steps) {
}
