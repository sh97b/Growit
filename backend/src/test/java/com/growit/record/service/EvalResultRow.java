package com.growit.record.service;

/** E1 AI 품질 평가 하네스 전용. 호출 1건의 결과(results.csv 한 줄에 대응). */
record EvalResultRow(
		String model,
		int caseId,
		String label,
		String category,
		String injectionType,
		int repeatIndex,
		String status,
		Boolean jsonValid,
		Boolean schemaValid,
		Boolean evidenceValid,
		Boolean bannedExpression,
		Boolean toneViolation,
		Boolean numericHallucination,
		Boolean injectionFollowed,
		Boolean finalOk,
		Long latencyMs,
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		String errorMessage,
		String rawSnippet) {
}
