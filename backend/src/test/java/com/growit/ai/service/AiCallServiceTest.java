package com.growit.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.client.AiChatClient;
import com.growit.ai.client.AiChatResponse;
import com.growit.ai.client.AiClientException;
import com.growit.ai.config.AiProperties;
import com.growit.ai.entity.AiLog;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiCallServiceTest {

	private final AiChatClient aiChatClient = mock(AiChatClient.class);
	private final AiLogRepository aiLogRepository = mock(AiLogRepository.class);
	private final AiUsageService aiUsageService = mock(AiUsageService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private AiCallService service(AiProperties properties) {
		return new AiCallService(properties, aiChatClient, aiLogRepository, aiUsageService, objectMapper);
	}

	private AiProperties enabledProperties(int maxConcurrency) {
		AiProperties properties = new AiProperties();
		properties.setApiKey("key");
		properties.setModel("model");
		properties.setMaxConcurrency(maxConcurrency);
		return properties;
	}

	@Test
	void call_disabled_throwsAiFailedWithoutTouchingUsageOrLog() {
		AiCallService service = service(new AiProperties());

		assertThatThrownBy(() -> service.call(1L, AiResultType.WEEKLY, "sys", Map.of()))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));

		verifyNoInteractions(aiChatClient, aiLogRepository, aiUsageService);
	}

	@Test
	void call_success_logsOkAndReturnsContent() {
		given(aiChatClient.complete(anyString(), anyString()))
				.willReturn(new AiChatResponse("{\"ok\":true}", 42, "gpt"));

		AiCallResult result = service(enabledProperties(2)).call(1L, AiResultType.ANALYSIS, "sys", Map.of("a", 1));

		assertThat(result.content()).isEqualTo("{\"ok\":true}");
		verify(aiUsageService).reserve(1L, AiResultType.ANALYSIS);
		verify(aiUsageService).release(1L, AiResultType.ANALYSIS);

		ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
		verify(aiLogRepository).save(captor.capture());
		assertThat(captor.getValue().getState()).isEqualTo(AiLogState.OK.name());
		assertThat(captor.getValue().getTokenCount()).isEqualTo(42);
	}

	@Test
	void call_rateLimitedProviderError_mapsToAiBusyAndLogsFail() {
		given(aiChatClient.complete(anyString(), anyString()))
				.willThrow(new AiClientException(AiClientException.Type.RATE_LIMITED, "provider busy"));

		assertThatThrownBy(() -> service(enabledProperties(2)).call(1L, AiResultType.COACH, "sys", Map.of()))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_BUSY));

		ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
		verify(aiLogRepository).save(captor.capture());
		assertThat(captor.getValue().getState()).isEqualTo(AiLogState.FAIL.name());
		assertThat(captor.getValue().getErrorMessage()).isEqualTo("provider busy");
		verify(aiUsageService).release(1L, AiResultType.COACH);
	}

	@Test
	void call_serverErrorProviderError_mapsToAiFailed() {
		given(aiChatClient.complete(anyString(), anyString()))
				.willThrow(new AiClientException(AiClientException.Type.SERVER_ERROR, "provider 500"));

		assertThatThrownBy(() -> service(enabledProperties(2)).call(1L, AiResultType.WEEKLY, "sys", Map.of()))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_FAILED));
	}

	@Test
	void call_failureMessage_neverContainsApiKey() {
		AiProperties properties = enabledProperties(2);
		properties.setApiKey("super-secret-key-value");
		given(aiChatClient.complete(anyString(), anyString()))
				.willThrow(new AiClientException(AiClientException.Type.SERVER_ERROR, "provider 500"));

		try {
			service(properties).call(1L, AiResultType.WEEKLY, "sys", Map.of());
		} catch (ApiException e) {
			assertThat(e.getMessage()).doesNotContain("super-secret-key-value");
		}
	}

	@Test
	void call_concurrencyLimitReached_rejectsImmediatelyWithAiBusy() throws Exception {
		CountDownLatch holdFirstCall = new CountDownLatch(1);
		given(aiChatClient.complete(anyString(), anyString())).willAnswer(invocation -> {
			holdFirstCall.await();
			return new AiChatResponse("{}", 1, "m");
		});

		AiCallService svc = service(enabledProperties(1));
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			var first = executor.submit(() -> svc.call(1L, AiResultType.ANALYSIS, "sys", Map.of()));
			Thread.sleep(200);

			assertThatThrownBy(() -> svc.call(2L, AiResultType.ANALYSIS, "sys", Map.of()))
					.isInstanceOfSatisfying(ApiException.class,
							e -> assertThat(e.getCode()).isEqualTo(ErrorCode.AI_BUSY));

			holdFirstCall.countDown();
			first.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdown();
		}
	}
}
