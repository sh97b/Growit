package com.growit.record.dto;

/** reason·evidence는 예전 결과에는 없다(null이면 화면이 그 줄을 생략한다). */
public record RecordNextAction(String text, Integer minutes, String state, String reason,
		RecordRelatedRecord evidence) {
}
