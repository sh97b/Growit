package com.growit.record.dto;

/** 오늘 기록이 로드맵 항목을 해 본 것으로 보일 때의 제안. text·stepTitle은 로드맵에 저장된 값이다. */
public record RecordRoadmapSuggestion(Long itemId, String text, String stepTitle) {
}
