package com.growit.roadmap.dto.response;

import java.util.List;

public record RoadmapStepResponse(Long id, int position, String title, String periodHint,
		List<RoadmapItemResponse> items) {
}
