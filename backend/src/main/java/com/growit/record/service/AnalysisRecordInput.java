package com.growit.record.service;

/**
 * feelingDirection(GOOD·NEUTRAL·HARD)은 힘든 날에는 위로를 먼저 건네도록 알려 주는 값이다. id는 AI가 "이 기록"을 nextAction의 근거로
 * 가리킬 수 있게 넘긴다(history의 기록과 같은 방식). achievement는 서버가 계산한 성취형 여부(어려움을 넘기거나 해낸 기록)로,
 * true이면 AI가 구체적 사실을 짚은 뒤 직접 인정을 한 번 건넨다.
 */
record AnalysisRecordInput(String date, String content, String difficulty, String feeling, Integer minutes,
		Long id, String feelingDirection, boolean achievement) {

	AnalysisRecordInput(String date, String content, String difficulty, String feeling, Integer minutes, Long id,
			String feelingDirection) {
		this(date, content, difficulty, feeling, minutes, id, feelingDirection, false);
	}

	AnalysisRecordInput(String date, String content, String difficulty, String feeling, Integer minutes) {
		this(date, content, difficulty, feeling, minutes, null, null, false);
	}
}
