package com.growit.ai.dto;

import java.util.List;

public record AiCoachHistoryResponse(List<AiCoachResponse> items, long totalCount) {
}
