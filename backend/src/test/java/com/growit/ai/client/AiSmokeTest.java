package com.growit.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 공용 키로 1회만 호출해 연결과 응답 형태를 확인하는 수동 점검용 테스트.
 * AI_API_KEY, AI_MODEL 환경변수가 모두 있을 때만 실행되고, 평소 ./gradlew test에서는 건너뛴다.
 */
@EnabledIfEnvironmentVariable(named = "AI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AI_MODEL", matches = ".+")
class AiSmokeTest {

	@Test
	void callsRealApiOnceAndPrintsDiagnostics() throws Exception {
		String apiKey = System.getenv("AI_API_KEY");
		String model = System.getenv("AI_MODEL");
		String baseUrl = System.getenv().getOrDefault("AI_BASE_URL", "https://api.openai.com/v1");

		ObjectMapper objectMapper = new ObjectMapper();
		String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
				"model", model,
				"max_completion_tokens", 20,
				"response_format", java.util.Map.of("type", "json_object"),
				"messages", java.util.List.of(
						java.util.Map.of("role", "system", "content", "Reply with a short JSON object only."),
						java.util.Map.of("role", "user", "content", "{\"ping\":true}"))));

		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl + "/chat/completions"))
				.timeout(Duration.ofSeconds(20))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody))
				.build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		JsonNode root = objectMapper.readTree(response.body());
		assertThat(root.has("choices")).isTrue();

		System.out.println("[AiSmokeTest] model: " + root.path("model").asText());
		System.out.println("[AiSmokeTest] usage: " + root.path("usage"));
		response.headers().map().forEach((name, values) -> {
			if (name.toLowerCase(Locale.ROOT).startsWith("x-ratelimit")) {
				System.out.println("[AiSmokeTest] " + name + ": " + values);
			}
		});
	}
}
