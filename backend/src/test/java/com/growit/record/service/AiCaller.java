package com.growit.record.service;

/** E1 AI 품질 평가 하네스 전용. 실제 호출(OpenAiRestCaller)과 가짜 호출(스모크 테스트)을 바꿔 끼우기 위한 경계. */
interface AiCaller {

	EvalAiResponse call(String model, String systemPrompt, String userContent) throws Exception;
}
