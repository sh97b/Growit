package com.growit.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.config.AiProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiChatClient implements AiChatClient {

	/** 429여도 속도 제한이 아니라 결제·한도 문제인 코드. */
	private static final java.util.Set<String> PROVIDER_DOWN_SUBS = java.util.Set.of(
			"insufficient_quota", "organization_spend_limit_exceeded", "billing_hard_limit_reached",
			"billing_not_active", "access_terminated");

	private final AiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public OpenAiChatClient(AiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		int timeoutMs = properties.getTimeoutSeconds() * 1000;
		requestFactory.setConnectTimeout(timeoutMs);
		requestFactory.setReadTimeout(timeoutMs);

		this.restClient = RestClient.builder()
				.baseUrl(properties.getBaseUrl())
				.requestFactory(requestFactory)
				.build();
	}

	@Override
	public AiChatResponse complete(String systemPrompt, String userContent) {
		return complete(systemPrompt, userContent, 0);
	}

	@Override
	public AiChatResponse complete(String systemPrompt, String userContent, int minOutputTokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("max_completion_tokens", Math.max(properties.getMaxOutputTokens(), minOutputTokens));
		body.put("response_format", Map.of("type", "json_object"));
		body.put("messages", List.of(
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userContent)));

		String raw;
		try {
			raw = restClient.post()
					.uri("/chat/completions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.body(String.class);
		} catch (HttpStatusCodeException e) {
			int status = e.getStatusCode().value();
			String sub = errorSubOf(e, status);
			if (status == 429 && !PROVIDER_DOWN_SUBS.contains(sub)) {
				throw new AiClientException(AiClientException.Type.RATE_LIMITED, "AI 제공자가 요청을 거절함(429)", sub);
			}
			if (status == 401 || status == 403 || status == 404 || (status == 429)) {
				throw new AiClientException(AiClientException.Type.PROVIDER_DOWN,
						"AI 제공자를 쓸 수 없음(" + status + ")", sub);
			}
			if (e.getStatusCode().is5xxServerError()) {
				throw new AiClientException(AiClientException.Type.SERVER_ERROR,
						"AI 제공자 오류(" + e.getStatusCode().value() + ")");
			}
			throw new AiClientException(AiClientException.Type.UNKNOWN,
					"AI 제공자 오류(" + e.getStatusCode().value() + ")");
		} catch (RestClientException e) {
			if (isTimeout(e)) {
				throw new AiClientException(AiClientException.Type.TIMEOUT, "AI 제공자 호출 시간 초과");
			}
			throw new AiClientException(AiClientException.Type.UNKNOWN, "AI 제공자 호출에 실패함");
		}

		return parse(raw);
	}

	/** 응답 본문의 error.code(없으면 error.type)만 꺼낸다. 영문 소문자·숫자·밑줄만 남겨 사용자 글이 섞일 여지를 없앤다. */
	private String errorSubOf(HttpStatusCodeException e, int status) {
		try {
			JsonNode error = objectMapper.readTree(e.getResponseBodyAsString()).path("error");
			String value = error.path("code").isTextual() ? error.path("code").asText()
					: error.path("type").isTextual() ? error.path("type").asText() : "";
			if (value.matches("[a-z0-9_]{1,60}")) {
				return value;
			}
		} catch (Exception ignored) {
			// 본문이 JSON이 아니면 상태 코드만 쓴다.
		}
		return "http_" + status;
	}

	private boolean isTimeout(Throwable e) {
		Throwable cause = e;
		while (cause != null) {
			if (cause instanceof java.net.SocketTimeoutException) {
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	private AiChatResponse parse(String raw) {
		JsonNode root;
		try {
			root = objectMapper.readTree(raw);
		} catch (Exception e) {
			throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답을 JSON으로 읽지 못함");
		}

		JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
		if (!contentNode.isTextual()) {
			throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답에 content가 없음");
		}

		JsonNode tokensNode = root.path("usage").path("total_tokens");
		Integer tokenCount = tokensNode.isInt() ? tokensNode.asInt() : null;
		String model = root.path("model").isTextual() ? root.path("model").asText() : null;

		return new AiChatResponse(contentNode.asText(), tokenCount, model);
	}
}
