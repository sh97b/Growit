package com.growit.ai.service;

import java.util.List;

/** ai_results.result에 그대로 저장되는 JSON 모양과 1:1로 대응한다(WEEKLY·METRICS 공통). */
record StoredViewResult(String text, List<Long> evidenceIds, String editedText) {

	StoredViewResult withEditedText(String editedText) {
		return new StoredViewResult(text, evidenceIds, editedText);
	}
}
