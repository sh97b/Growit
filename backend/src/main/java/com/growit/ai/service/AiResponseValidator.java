package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiResponseValidator {

	private final ObjectMapper objectMapper;

	public AiResponseValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public JsonNode parse(String rawJson) {
		try {
			return objectMapper.readTree(rawJson);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
	}

	public void requireFields(JsonNode node, String... fieldNames) {
		for (String field : fieldNames) {
			if (!node.hasNonNull(field)) {
				throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
			}
		}
	}
}
