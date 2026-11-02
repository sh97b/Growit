package com.growit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class AiResponseValidatorTest {

	private final AiResponseValidator validator = new AiResponseValidator(new ObjectMapper());

	@Test
	void parse_validJson_returnsNode() {
		JsonNode node = validator.parse("{\"summary\":\"내용\"}");

		assertThat(node.path("summary").asText()).isEqualTo("내용");
	}

	@Test
	void parse_invalidJson_throwsAiFailed() {
		assertThatThrownBy(() -> validator.parse("not json"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
	}

	@Test
	void requireFields_missingField_throwsAiFailed() {
		JsonNode node = validator.parse("{\"summary\":\"내용\"}");

		assertThatThrownBy(() -> validator.requireFields(node, "summary", "recordIds"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
	}

	@Test
	void requireFields_allPresent_doesNotThrow() {
		JsonNode node = validator.parse("{\"summary\":\"내용\",\"recordIds\":[1,2]}");

		validator.requireFields(node, "summary", "recordIds");
	}
}
