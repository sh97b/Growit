package com.growit.record.service;

import java.util.List;

/** records.ai_analysis에 그대로 저장되는 JSON 모양과 1:1로 대응한다. */
record StoredAnalysis(
		String title,
		List<String> tags,
		List<Long> relatedRecordIds,
		StoredChange change,
		String tagsFeedback,
		String changeFeedback) {

	StoredAnalysis withTagsFeedback(String feedback) {
		return new StoredAnalysis(title, tags, relatedRecordIds, change, feedback, changeFeedback);
	}

	StoredAnalysis withChangeFeedback(String feedback) {
		return new StoredAnalysis(title, tags, relatedRecordIds, change, tagsFeedback, feedback);
	}

	StoredAnalysis withEditedTags(List<String> newTags) {
		return new StoredAnalysis(title, newTags, relatedRecordIds, change, "EDITED", changeFeedback);
	}
}
