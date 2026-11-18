package com.growit.dot.dto;

import java.util.List;

public record DotPresetResponse(String key, String label, int width, int height, List<String> palette,
		List<String> pixels) {
}
