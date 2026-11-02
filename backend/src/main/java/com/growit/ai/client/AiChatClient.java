package com.growit.ai.client;

public interface AiChatClient {

	/** systemPrompt와 userContent를 그대로 OpenAI 호환 /chat/completions에 보내고 content를 돌려받는다. */
	AiChatResponse complete(String systemPrompt, String userContent);
}
