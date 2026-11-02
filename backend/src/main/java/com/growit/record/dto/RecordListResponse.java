package com.growit.record.dto;

import java.util.List;

public record RecordListResponse(List<RecordResponse> items, long totalCount) {
}
