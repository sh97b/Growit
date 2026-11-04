package com.growit.ai.service;

import java.util.List;

/** ai_results.result에 그대로 저장되는 JSON 모양과 1:1로 대응한다(COACH). */
record StoredCoachResult(
		String mode,
		String periodSummary,
		List<StoredCoachTextItem> changes,
		List<StoredCoachTextItem> repeatedDifficulties,
		StoredCoachAction nextAction,
		StoredCoachAction comebackAction,
		int recordsRead) {
}
