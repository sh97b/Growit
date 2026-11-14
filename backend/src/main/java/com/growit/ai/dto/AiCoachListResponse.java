package com.growit.ai.dto;

import java.util.List;

public record AiCoachListResponse(List<AiCoachListItemResponse> items, long totalCount) {
}
