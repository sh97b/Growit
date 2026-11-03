package com.growit.record.dto;

import java.util.List;

public record RecordAnalysisResponse(
		String title,
		List<String> tags,
		List<RecordRelatedRecord> relatedRecords,
		RecordAnalysisChange change,
		String tagsFeedback,
		String changeFeedback) {
}
