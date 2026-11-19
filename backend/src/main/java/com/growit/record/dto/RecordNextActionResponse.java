package com.growit.record.dto;

import java.time.LocalDate;

/** 가장 최근 기록의 "다음에 해 볼 일". state가 null이면 아직 반응이 없는 제안이다. */
public record RecordNextActionResponse(Long recordId, LocalDate recordDate, String text, Integer minutes,
		String state, String reason, RecordRelatedRecord evidence) {
}
