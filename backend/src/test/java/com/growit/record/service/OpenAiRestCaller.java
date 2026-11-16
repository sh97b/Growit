package com.growit.record.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * E1 AI 품질 평가 하네스 전용(실제 네트워크 호출). 요청 모양(model, max_completion_tokens,
 * response_format=json_object, messages)은 프로덕션 OpenAiChatClient와 같게 맞췄지만, 모델별로
 * 다른 AiProperties를 매번 새로 만들어야 하고 usage.prompt_tokens·completion_tokens 세부 값까지
 * 읽어야 해서(AiChatResponse는 total_tokens만 노출) 별도로 둔다. temperature 등 거절당하는 파라미터는
 * 아예 보내지 않는다.
 */
class OpenAiRestCaller implements AiCaller {

	private final String apiKey;
	private final int maxOutputTokens;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final RestClient restClient;

	OpenAiRestCaller(String baseUrl, String apiKey, int timeoutSeconds, int maxOutputTokens) {
		this.apiKey = apiKey;
		this.maxOutputTokens = maxOutputTokens;

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		int timeoutMs = timeoutSeconds * 1000;
		requestFactory.setConnectTimeout(timeoutMs);
		requestFactory.setReadTimeout(timeoutMs);

		this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
	}

	@Override
	public EvalAiResponse call(String model, String systemPrompt, String userContent) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model);
		body.put("max_completion_tokens", maxOutputTokens);
		body.put("response_format", Map.of("type", "json_object"));
		body.put("messages", List.of(
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userContent)));

		long startedAt = System.currentTimeMillis();
		String raw = restClient.post()
				.uri("/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(String.class);
		long latencyMs = System.currentTimeMillis() - startedAt;

		JsonNode root = objectMapper.readTree(raw);
		JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
		if (!contentNode.isTextual()) {
			throw new IllegalStateException("AI 응답에 content가 없음");
		}
		JsonNode usage = root.path("usage");
		Integer promptTokens = usage.path("prompt_tokens").isInt() ? usage.path("prompt_tokens").asInt() : null;
		Integer completionTokens =
				usage.path("completion_tokens").isInt() ? usage.path("completion_tokens").asInt() : null;
		Integer totalTokens = usage.path("total_tokens").isInt() ? usage.path("total_tokens").asInt() : null;
		String returnedModel = root.path("model").isTextual() ? root.path("model").asText() : model;

		return new EvalAiResponse(contentNode.asText(), promptTokens, completionTokens, totalTokens, returnedModel,
				latencyMs);
	}
}
