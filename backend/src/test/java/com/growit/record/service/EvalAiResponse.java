package com.growit.record.service;

/**
 * E1 AI 품질 평가 하네스 전용. OpenAiChatClient(AiChatResponse)는 usage.total_tokens만 돌려주고
 * prompt_tokens·completion_tokens 구분이 없어, 평가 지표에 필요한 세부 토큰 수를 얻으려고 별도의
 * 가벼운 호출자(AiCaller)를 둔다. 프로덕션 코드는 건드리지 않는다.
 */
record EvalAiResponse(
		String content,
		Integer promptTokens,
		Integer completionTokens,
		Integer totalTokens,
		String returnedModel,
		long latencyMs) {
}
