package com.growit.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 실제 공급자 대신 로컬 가짜 서버로 429·5xx·시간 초과·잘못된 JSON 변환을 확인한다(비밀 값 불필요). */
class OpenAiChatClientTest {

	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	private AiProperties properties(int port, int timeoutSeconds) {
		AiProperties properties = new AiProperties();
		properties.setApiKey("test-key");
		properties.setModel("test-model");
		properties.setBaseUrl("http://localhost:" + port);
		properties.setTimeoutSeconds(timeoutSeconds);
		properties.setMaxOutputTokens(100);
		return properties;
	}

	private HttpServer startServer(int status, String body) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/chat/completions", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		this.server = server;
		return server;
	}

	@Test
	void complete_success_parsesContentAndUsage() throws IOException {
		String body = "{\"model\":\"test-model\",\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}],"
				+ "\"usage\":{\"total_tokens\":55}}";
		HttpServer server = startServer(200, body);
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		AiChatResponse response = client.complete("system", "{}");

		assertThat(response.content()).isEqualTo("{\"ok\":true}");
		assertThat(response.tokenCount()).isEqualTo(55);
		assertThat(response.model()).isEqualTo("test-model");
	}

	@Test
	void complete_tooManyRequests_throwsRateLimited() throws IOException {
		HttpServer server = startServer(429, "{\"error\":\"rate limited\"}");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.RATE_LIMITED));
	}

	@Test
	void complete_quotaExhausted429_isProviderDownWithSub() throws IOException {
		HttpServer server = startServer(429, "{\"error\":{\"code\":\"insufficient_quota\",\"message\":\"secret text\"}}");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class, e -> {
					assertThat(e.getType()).isEqualTo(AiClientException.Type.PROVIDER_DOWN);
					assertThat(e.getSub()).isEqualTo("insufficient_quota");
					assertThat(e.getMessage()).doesNotContain("secret");
				});
	}

	@Test
	void complete_rateLimit429WithCode_staysRateLimited() throws IOException {
		HttpServer server = startServer(429, "{\"error\":{\"code\":\"rate_limit_exceeded\"}}");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.RATE_LIMITED));
	}

	@Test
	void complete_unauthorizedAndForbidden_areProviderDown() throws IOException {
		for (int status : new int[] {401, 403}) {
			HttpServer server = startServer(status, "{\"error\":{\"code\":\"invalid_api_key\"}}");
			OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

			assertThatThrownBy(() -> client.complete("system", "{}"))
					.isInstanceOfSatisfying(AiClientException.class,
							e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.PROVIDER_DOWN));
			server.stop(0);
		}
	}

	@Test
	void complete_serverError_throwsServerError() throws IOException {
		HttpServer server = startServer(500, "{\"error\":\"boom\"}");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.SERVER_ERROR));
	}

	@Test
	void complete_malformedJsonBody_throwsInvalidResponse() throws IOException {
		HttpServer server = startServer(200, "not json");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.INVALID_RESPONSE));
	}

	@Test
	void complete_missingContentField_throwsInvalidResponse() throws IOException {
		HttpServer server = startServer(200, "{\"choices\":[{\"message\":{}}]}");
		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 5), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.INVALID_RESPONSE));
	}

	@Test
	void complete_timeout_throwsTimeout() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/chat/completions", exchange -> {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, 2);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write("{}".getBytes(StandardCharsets.UTF_8));
			}
		});
		server.start();
		this.server = server;

		OpenAiChatClient client = new OpenAiChatClient(properties(server.getAddress().getPort(), 1), new ObjectMapper());

		assertThatThrownBy(() -> client.complete("system", "{}"))
				.isInstanceOfSatisfying(AiClientException.class,
						e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.TIMEOUT));
	}

	/** 받은 요청(경로·Authorization·본문)을 모으고, 미리 정한 응답을 차례로 돌려주는 가짜 서버. */
	private static final class Recorder {
		final java.util.List<String> paths = new java.util.ArrayList<>();
		final java.util.List<String> auth = new java.util.ArrayList<>();
		final java.util.List<String> bodies = new java.util.ArrayList<>();
	}

	private Recorder startRecording(java.util.List<Object[]> responses) throws IOException {
		Recorder recorder = new Recorder();
		java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
		HttpServer s = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		s.createContext("/", exchange -> {
			recorder.paths.add(exchange.getRequestURI().getPath());
			recorder.auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
			recorder.bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			Object[] response = responses.get(Math.min(index.getAndIncrement(), responses.size() - 1));
			byte[] bytes = ((String) response[1]).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders((Integer) response[0], bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		s.start();
		this.server = s;
		return recorder;
	}

	private static String okBody(String content, String finishReason) {
		return "{\"model\":\"m\",\"choices\":[{\"finish_reason\":\"" + finishReason
				+ "\",\"message\":{\"content\":" + (content == null ? "null" : "\"" + content + "\"")
				+ "}}],\"usage\":{\"total_tokens\":9}}";
	}

	private AiProperties props(String provider, int port, int maxTokens) {
		AiProperties properties = new AiProperties();
		properties.setProvider(provider);
		properties.setApiKey("secret-key");
		properties.setModel("grok-4.20-0309-non-reasoning");
		properties.setBaseUrl("http://localhost:" + port + "/v1");
		properties.setMaxOutputTokens(maxTokens);
		return properties;
	}

	private java.util.List<String> captureClientLogs() {
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(OpenAiChatClient.class);
		ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
				new ch.qos.logback.core.read.ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		return new java.util.AbstractList<>() {
			@Override
			public String get(int i) {
				return appender.list.get(i).getFormattedMessage();
			}

			@Override
			public int size() {
				return appender.list.size();
			}
		};
	}

	@Test
	void request_isSameShapeForOpenAiAndXai_withBaseUrlPlusChatCompletions() throws IOException {
		for (String provider : new String[] {"openai", "xai"}) {
			Recorder recorder = startRecording(java.util.List.<Object[]>of(new Object[] {200, okBody("{}", "stop")}));
			OpenAiChatClient client = new OpenAiChatClient(props(provider, server.getAddress().getPort(), 700),
					new ObjectMapper());

			AiChatResponse response = client.complete("sys", "{\"a\":1}");

			assertThat(response.content()).isEqualTo("{}");
			assertThat(recorder.paths).containsExactly("/v1/chat/completions");
			assertThat(recorder.auth).containsExactly("Bearer secret-key");
			com.fasterxml.jackson.databind.JsonNode body = new ObjectMapper().readTree(recorder.bodies.get(0));
			assertThat(body.path("model").asText()).isEqualTo("grok-4.20-0309-non-reasoning");
			assertThat(body.path("max_completion_tokens").asInt()).isEqualTo(700);
			assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
			assertThat(body.path("messages")).hasSize(2);
			// 업체별 비표준 옵션은 처음부터 보내지 않는다.
			assertThat(body.fieldNames()).toIterable()
					.containsExactlyInAnyOrder("model", "max_completion_tokens", "response_format", "messages");
			server.stop(0);
		}
	}

	@Test
	void defaults_matchOldBehavior_andXaiHasItsOwnBaseUrl() {
		AiProperties defaults = new AiProperties();
		assertThat(defaults.getProvider()).isEqualTo("openai");
		assertThat(defaults.getBaseUrl()).isEqualTo("https://api.openai.com/v1");

		AiProperties xai = new AiProperties();
		xai.setProvider("XAI");
		assertThat(xai.getProvider()).isEqualTo("xai");
		assertThat(xai.getBaseUrl()).isEqualTo("https://api.x.ai/v1");
		xai.setBaseUrl("https://example.test/v1");
		assertThat(xai.getBaseUrl()).isEqualTo("https://example.test/v1");

		AiProperties unknown = new AiProperties();
		unknown.setProvider("something");
		assertThat(unknown.getProvider()).isEqualTo("openai");
	}

	@Test
	void emptyContent_retriesOnceWithDoubledTokens_thenSucceeds() throws IOException {
		Recorder recorder = startRecording(java.util.List.<Object[]>of(new Object[] {200, okBody(null, "stop")},
				new Object[] {200, okBody("{}", "stop")}));
		OpenAiChatClient client = new OpenAiChatClient(props("xai", server.getAddress().getPort(), 1500),
				new ObjectMapper());

		AiChatResponse response = client.complete("sys", "{}");

		assertThat(response.content()).isEqualTo("{}");
		assertThat(recorder.bodies).hasSize(2);
		assertThat(new ObjectMapper().readTree(recorder.bodies.get(1)).path("max_completion_tokens").asInt())
				.isEqualTo(3000);
	}

	@Test
	void lengthFinish_retryDoublesWithCap3000_andLogsTruncatedWhenStillCut() throws IOException {
		Recorder recorder = startRecording(java.util.List.<Object[]>of(new Object[] {200, okBody("{\\\"a", "length")}));
		java.util.List<String> logs = captureClientLogs();
		OpenAiChatClient client = new OpenAiChatClient(props("xai", server.getAddress().getPort(), 2000),
				new ObjectMapper());

		assertThatThrownBy(() -> client.complete("sys", "{}")).isInstanceOfSatisfying(AiClientException.class,
				e -> assertThat(e.getType()).isEqualTo(AiClientException.Type.INVALID_RESPONSE));

		assertThat(recorder.bodies).hasSize(2);
		assertThat(new ObjectMapper().readTree(recorder.bodies.get(1)).path("max_completion_tokens").asInt())
				.isEqualTo(3000);
		assertThat(logs).contains("AI_TRUNCATED provider=xai model=grok-4.20-0309-non-reasoning");
	}

	@Test
	void errorClassification_handlesOpenAiAndXaiBodies_andUnknownIsOther() throws IOException {
		Object[][] cases = {
				{429, "{\"code\":\"rate_limit\",\"error\":\"Too many requests\"}", AiClientException.Type.RATE_LIMITED, "rate_limit"},
				{429, "{\"error\":{\"code\":\"insufficient_quota\"}}", AiClientException.Type.PROVIDER_DOWN, "insufficient_quota"},
				{401, "{\"code\":\"Client specified an invalid argument\",\"error\":\"Incorrect API key\"}", AiClientException.Type.PROVIDER_DOWN, "OTHER"},
				{403, "<html>forbidden</html>", AiClientException.Type.PROVIDER_DOWN, "OTHER"},
				{503, "oops", AiClientException.Type.SERVER_ERROR, null},
		};
		for (Object[] c : cases) {
			startRecording(java.util.List.<Object[]>of(new Object[] {c[0], c[1]}));
			OpenAiChatClient client = new OpenAiChatClient(props("xai", server.getAddress().getPort(), 100),
					new ObjectMapper());

			assertThatThrownBy(() -> client.complete("sys", "{}")).isInstanceOfSatisfying(AiClientException.class, e -> {
				assertThat(e.getType()).isEqualTo(c[2]);
				if (c[3] != null) {
					assertThat(e.getSub()).isEqualTo(c[3]);
				}
				assertThat(e.getMessage()).doesNotContain("secret-key").doesNotContain("Incorrect API key");
			});
			server.stop(0);
		}
	}

	@Test
	void reasoningModelName_warnsAtStartup_butNonReasoningDoesNot() {
		java.util.List<String> logs = captureClientLogs();
		AiProperties reasoning = new AiProperties();
		reasoning.setModel("grok-4.20-0309-reasoning");
		new OpenAiChatClient(reasoning, new ObjectMapper());
		assertThat(logs).anyMatch(l -> l.startsWith("AI_MODEL_REASONING provider=openai model=grok-4.20-0309-reasoning"));

		int before = logs.size();
		AiProperties nonReasoning = new AiProperties();
		nonReasoning.setModel("grok-4.20-0309-non-reasoning");
		new OpenAiChatClient(nonReasoning, new ObjectMapper());
		assertThat(logs.size()).isEqualTo(before);
	}
}
