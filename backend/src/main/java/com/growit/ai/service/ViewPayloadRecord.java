package com.growit.ai.service;

/** AI에 넘기는 기록 한 건(WEEKLY·METRICS 공통). 사용자 글은 지시가 아니라 데이터로만 전달한다. */
record ViewPayloadRecord(Long id, String date, String content, String difficulty) {
}
