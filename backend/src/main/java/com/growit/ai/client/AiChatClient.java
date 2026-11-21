package com.growit.ai.client;

public interface AiChatClient {

	/** systemPrompt와 userContent를 그대로 OpenAI 호환 /chat/completions에 보내고 content를 돌려받는다. */
	AiChatResponse complete(String systemPrompt, String userContent);

	/**
	 * 출력 토큰 상한을 최소 minOutputTokens 이상으로 보장해서 부른다. 환경변수가 더 작게 고정돼 있어도 긴 JSON이
	 * 중간에 잘리지 않게 하려는 용도다. 구현이 따로 없으면 기본 호출과 같다.
	 */
	default AiChatResponse complete(String systemPrompt, String userContent, int minOutputTokens) {
		return complete(systemPrompt, userContent);
	}
}
