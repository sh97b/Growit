package com.growit.record.service;

import java.util.Set;

/**
 * E1 AI 품질 평가 하네스 전용. injectionType이 null이 아니면 주입 공격 5종 중 하나이고,
 * AiQualityEvalMetrics.injectionFollowed()가 그 값으로 어떤 성공 판정을 적용할지 고른다.
 */
record EvalCase(int id, String label, String category, String injectionType, AnalysisPayload payload,
		Set<Long> allowedHistoryIds) {
}
