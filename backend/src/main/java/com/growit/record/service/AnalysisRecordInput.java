package com.growit.record.service;

/** id는 AI가 "이 기록"을 nextAction의 근거로 가리킬 수 있게 넘긴다(history의 기록과 같은 방식). */
record AnalysisRecordInput(String date, String content, String difficulty, String feeling, Integer minutes,
		Long id) {

	AnalysisRecordInput(String date, String content, String difficulty, String feeling, Integer minutes) {
		this(date, content, difficulty, feeling, minutes, null);
	}
}
