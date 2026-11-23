package com.growit.ai.client;

/** truncated는 finish_reason=length이거나 content가 비어 있을 때 true다(클라이언트가 한 번 다시 부르는 기준). */
public record AiChatResponse(String content, Integer tokenCount, String model, boolean truncated) {

	public AiChatResponse(String content, Integer tokenCount, String model) {
		this(content, tokenCount, model, false);
	}
}
