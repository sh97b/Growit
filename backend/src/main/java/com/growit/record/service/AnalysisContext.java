package com.growit.record.service;

/** 서버가 계산한 맥락. daysSincePreviousRecord는 첫 기록이면 null이다(AI는 이 값을 세지 않고 그대로 읽는다). */
record AnalysisContext(boolean isFirstRecord, Integer daysSincePreviousRecord) {
}
