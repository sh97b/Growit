package com.growit.record.service;

import java.util.List;

/**
 * records.ai_analysis에 그대로 저장되는 JSON 모양과 1:1로 대응한다. promptVersion·model은 어떤 지시문
 * 버전·모델이 만든 결과인지 남겨 나중에 비교하기 위한 것이라, 그 값이 없던 예전 저장값을 읽을 때도
 * null로만 채워지고 오류가 나지 않는다.
 */
record StoredAnalysis(
		String title,
		List<String> tags,
		List<Long> relatedRecordIds,
		StoredChange change,
		String tagsFeedback,
		String changeFeedback,
		Integer promptVersion,
		String model) {

	StoredAnalysis withTagsFeedback(String feedback) {
		return new StoredAnalysis(title, tags, relatedRecordIds, change, feedback, changeFeedback, promptVersion,
				model);
	}

	StoredAnalysis withChangeFeedback(String feedback) {
		return new StoredAnalysis(title, tags, relatedRecordIds, change, tagsFeedback, feedback, promptVersion,
				model);
	}

	StoredAnalysis withEditedTags(List<String> newTags) {
		return new StoredAnalysis(title, newTags, relatedRecordIds, change, "EDITED", changeFeedback, promptVersion,
				model);
	}
}
