package com.growit.record.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** roadmap은 분야에 진행 중인 로드맵이 있을 때만 값이 있고, 없으면 JSON에서 빠진다(기존 입력 그대로). */
record AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history,
		AnalysisContext context, @JsonInclude(JsonInclude.Include.NON_NULL) AnalysisRoadmapInput roadmap) {

	/** 맥락·로드맵 없이 쓰는 호출(품질 평가 하네스)용. */
	AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history) {
		this(hobby, record, history, null, null);
	}

	AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history,
			AnalysisContext context) {
		this(hobby, record, history, context, null);
	}
}
