package com.growit.record.dto;

/** text(구체적 변화)와 support(응원·위로)는 둘 중 하나가 null일 수 있다. 예전 결과는 support가 null이다. */
public record RecordAnalysisChange(String text, String support, RecordRelatedRecord evidence) {
}
