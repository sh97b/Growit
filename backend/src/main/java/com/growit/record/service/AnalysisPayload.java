package com.growit.record.service;

import java.util.List;

record AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history,
		AnalysisContext context) {

	/** 맥락 없이 쓰는 호출(품질 평가 하네스)용. */
	AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history) {
		this(hobby, record, history, null);
	}
}
