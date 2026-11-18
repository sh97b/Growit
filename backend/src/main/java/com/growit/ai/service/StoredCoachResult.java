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
		int recordsRead,
		/** 더 작게 바꾼 행동. 없으면(과거 결과 포함) null이고 nextAction/comebackAction이 그대로 현재 행동이다. */
		StoredCoachAction simplifiedAction,
		/** 새 구성(C)의 결과에만 있다. 예전 결과는 모두 null이라 예전 모양 그대로 보인다. */
		List<StoredCoachTextItem> flow,
		List<StoredCoachTextItem> resolvedDifficulties,
		List<StoredCoachTextItem> questions,
		List<StoredCoachTextItem> directions,
		StoredCoachStats stats) {

	StoredCoachResult(String mode, String periodSummary, List<StoredCoachTextItem> changes,
			List<StoredCoachTextItem> repeatedDifficulties, StoredCoachAction nextAction,
			StoredCoachAction comebackAction, int recordsRead) {
		this(mode, periodSummary, changes, repeatedDifficulties, nextAction, comebackAction, recordsRead, null, null,
				null, null, null, null);
	}

	StoredCoachResult(String mode, String periodSummary, List<StoredCoachTextItem> changes,
			List<StoredCoachTextItem> repeatedDifficulties, StoredCoachAction nextAction,
			StoredCoachAction comebackAction, int recordsRead, StoredCoachAction simplifiedAction) {
		this(mode, periodSummary, changes, repeatedDifficulties, nextAction, comebackAction, recordsRead,
				simplifiedAction, null, null, null, null, null);
	}

	boolean isReflection() {
		return flow != null;
	}
}
