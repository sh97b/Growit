package com.growit.record.service;

import java.util.List;

/** 현재 단계 제목과 그 단계의 미완료 항목(id는 AI가 roadmapSuggestion에서 가리키는 용도). */
record AnalysisRoadmapInput(String currentStep, List<AnalysisRoadmapItem> items) {

	record AnalysisRoadmapItem(Long id, String text) {
	}
}
