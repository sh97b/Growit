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

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OpenAiChatClient.class);
	/** 잘린 응답을 다시 받을 때 늘리는 최대 토큰의 상한. */
	private static final int TRUNCATED_RETRY_TOKEN_CAP = 3000;

	public OpenAiChatClient(AiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;

		String raw = properties.getRawProvider();
		if (raw != null && !raw.isBlank() && !AiProperties.PROVIDER_OPENAI.equalsIgnoreCase(raw.trim())
				&& !AiProperties.PROVIDER_XAI.equalsIgnoreCase(raw.trim())) {
			log.warn("AI_PROVIDER_UNKNOWN provider={} using=openai", raw.trim());
		}
		// 추론형 모델은 추론 토큰이 출력 한도를 먹어 JSON이 잘리기 쉽다. non-reasoning 모델을 권장한다.
		String model = properties.getModel() == null ? "" : properties.getModel().toLowerCase();
		if (model.contains("reasoning") && !model.contains("non-reasoning")) {
			log.warn("AI_MODEL_REASONING provider={} model={} hint=non-reasoning 모델을 쓰면 출력이 잘리지 않아요",
					properties.getProvider(), properties.getModel());
		}

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

	/**
	 * 응답이 잘렸거나(finish_reason=length) content가 비어 있으면 최대 토큰을 2배(상한 3000)로 한 번만 다시 부른다.
	 * 그래도 같으면 AI_TRUNCATED 로그를 남기고 실패로 본다.
	 */
	@Override
	public AiChatResponse complete(String systemPrompt, String userContent, int minOutputTokens) {
		int tokens = Math.max(properties.getMaxOutputTokens(), minOutputTokens);
		AiChatResponse first = request(systemPrompt, userContent, tokens);
		if (!first.truncated()) {
			return first;
		}
		int doubled = Math.min(TRUNCATED_RETRY_TOKEN_CAP, Math.max(tokens, tokens * 2));
		AiChatResponse second = request(systemPrompt, userContent, doubled);
		if (!second.truncated()) {
			return second;
		}
		log.warn("AI_TRUNCATED provider={} model={}", properties.getProvider(), properties.getModel());
		throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답이 잘렸거나 비어 있음", "truncated");
	}

	private AiChatResponse request(String systemPrompt, String userContent, int maxTokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("max_completion_tokens", maxTokens);
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
			// OpenAI는 {"error":{"code":..,"type":..}}, xAI는 {"code":"..","error":"문장"} 모양이라 둘 다 읽는다.
			JsonNode rootNode = objectMapper.readTree(e.getResponseBodyAsString());
			JsonNode error = rootNode.path("error");
			String value = error.path("code").isTextual() ? error.path("code").asText()
					: error.path("type").isTextual() ? error.path("type").asText()
					: rootNode.path("code").isTextual() ? rootNode.path("code").asText() : "";
			if (value.matches("[a-z0-9_]{1,60}")) {
				return value;
			}
		} catch (Exception ignored) {
			// 본문이 JSON이 아니면 모르는 형식이다.
		}
		// 모르는 형식은 OTHER, 상태 코드는 메시지에 따로 담긴다.
		return "OTHER";
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

		JsonNode choice = root.path("choices").path(0);
		JsonNode contentNode = choice.path("message").path("content");
		if (!contentNode.isTextual() && !contentNode.isNull() && !contentNode.isMissingNode()) {
			throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답에 content가 없음");
		}
		if (choice.isMissingNode()) {
			throw new AiClientException(AiClientException.Type.INVALID_RESPONSE, "AI 응답에 content가 없음");
		}
		String content = contentNode.isTextual() ? contentNode.asText() : "";
		boolean truncated = content.isBlank() || "length".equals(choice.path("finish_reason").asText(""));

		JsonNode tokensNode = root.path("usage").path("total_tokens");
		Integer tokenCount = tokensNode.isInt() ? tokensNode.asInt() : null;
		String model = root.path("model").isTextual() ? root.path("model").asText() : null;

		return new AiChatResponse(content, tokenCount, model, truncated);
	}
}
