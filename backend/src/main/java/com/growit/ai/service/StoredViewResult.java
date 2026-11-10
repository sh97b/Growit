package com.growit.ai.service;

import java.util.List;

/**
 * ai_results.result에 그대로 저장되는 JSON 모양과 1:1로 대응한다(WEEKLY·METRICS 공통).
 * remainingText·remainingEvidenceIds는 WEEKLY의 "아직 남은 어려움" 섹션 전용이며(promptVersion 2부터),
 * 그 전에 저장된 결과나 METRICS·COMPARE 결과에는 없어 null로 읽힌다 — "고치기"는 continued(text)만 대상이라
 * remaining에는 편집 칸을 따로 두지 않는다.
 */
record StoredViewResult(String text, List<Long> evidenceIds, String editedText, String remainingText,
		List<Long> remainingEvidenceIds) {

	StoredViewResult withEditedText(String editedText) {
		return new StoredViewResult(text, evidenceIds, editedText, remainingText, remainingEvidenceIds);
	}
}
