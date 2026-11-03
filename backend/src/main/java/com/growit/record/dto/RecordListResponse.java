package com.growit.record.dto;

import java.util.List;

public record RecordListResponse(List<RecordSummaryResponse> items, long totalCount) {
}
