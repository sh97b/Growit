package com.growit.ai.dto;

public record AiUsageResponse(boolean enabled, int limit, long used, long remaining) {
}
