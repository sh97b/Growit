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
}
