package com.growit.record.dto;

import java.util.List;

/**
 * 기록 목록 조회 응답.
 *
 * @param items      이번 페이지의 기록들(최신순)
 * @param totalCount 조건에 맞는 전체 기록 수(페이지 크기와 무관)
 */
public record RecordListResponse(List<RecordResponse> items, long totalCount) {
}
