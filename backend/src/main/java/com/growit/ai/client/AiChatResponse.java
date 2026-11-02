package com.growit.ai.client;

public record AiChatResponse(String content, Integer tokenCount, String model) {
}
