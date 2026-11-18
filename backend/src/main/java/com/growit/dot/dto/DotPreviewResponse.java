package com.growit.dot.dto;

import java.util.List;

public record DotPreviewResponse(String key, String label, int goalDays, int width, int height, List<String> palette,
		List<DotCell> cells) {
}
