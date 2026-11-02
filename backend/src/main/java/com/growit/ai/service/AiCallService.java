package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;

@Service
public class AiCallService {

	private static final String MESSAGE_FAILED = "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.";
	private static final String MESSAGE_BUSY = "지금은 AI 요청이 많아요. 기록은 안전하게 저장돼 있어요. 잠시 후 다시 시도해 주세요.";
	private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

	private final AiProperties properties;
	private final AiChatClient aiChatClient;
	private final AiLogRepository aiLogRepository;
	private final AiUsageService aiUsageService;
	private final ObjectMapper objectMapper;
	private final Semaphore concurrencyLimiter;

	public AiCallService(AiProperties properties, AiChatClient aiChatClient, AiLogRepository aiLogRepository,
			AiUsageService aiUsageService, ObjectMapper objectMapper) {
		this.properties = properties;
		this.aiChatClient = aiChatClient;
		this.aiLogRepository = aiLogRepository;
		this.aiUsageService = aiUsageService;
		this.objectMapper = objectMapper;
		this.concurrencyLimiter = new Semaphore(Math.max(1, properties.getMaxConcurrency()));
	}

	/**
	 * 한 번의 호출 흐름: AI 꺼짐 확인 → 한도 확인 → 동시 호출 제한 → 호출 → 결과와 상관없이 ai_logs 기록.
	 * 동시 호출 제한은 기다리지 않고 바로 거절한다(AI_BUSY). 요청을 쌓아두면 응답이 다 같이 느려지고 타임아웃이
	 * 겹쳐서 더 나빠지므로, 공용 키를 쓰는 상황에서는 빠르게 실패하는 쪽이 낫다고 판단했다.
	 */
	public AiCallResult call(Long userId, AiResultType type, String systemPrompt, Object userData) {
		if (!properties.isEnabled()) {
			throw new ApiException(ErrorCode.AI_FAILED, MESSAGE_FAILED);
		}

		aiUsageService.reserve(userId, type);

		if (!concurrencyLimiter.tryAcquire()) {
			aiUsageService.release(userId, type);
			aiLogRepository.save(new AiLog(userId, type.name(), AiLogState.LIMIT.name(), null, null,
					"동시 호출 한도를 넘어 거절함"));
			throw new ApiException(ErrorCode.AI_BUSY, MESSAGE_BUSY);
		}

		long startedAt = System.currentTimeMillis();
		try {
			String userContent;
			try {
				userContent = objectMapper.writeValueAsString(userData);
			} catch (JsonProcessingException e) {
				aiLogRepository.save(new AiLog(userId, type.name(), AiLogState.FAIL.name(),
						(int) (System.currentTimeMillis() - startedAt), null, "요청 데이터를 JSON으로 바꾸지 못함"));
				throw new ApiException(ErrorCode.AI_FAILED, MESSAGE_FAILED);
			}

			String safeSystemPrompt = systemPrompt + "\n\n" + AiSafetyPrompt.DATA_NOT_INSTRUCTION;
			AiChatResponse response;
			try {
				response = aiChatClient.complete(safeSystemPrompt, userContent);
			} catch (AiClientException e) {
				int responseMs = (int) (System.currentTimeMillis() - startedAt);
				aiLogRepository.save(new AiLog(userId, type.name(), AiLogState.FAIL.name(), responseMs, null,
						truncate(e.getMessage())));
				ErrorCode code = e.getType() == AiClientException.Type.RATE_LIMITED
						? ErrorCode.AI_BUSY
						: ErrorCode.AI_FAILED;
				throw new ApiException(code, code == ErrorCode.AI_BUSY ? MESSAGE_BUSY : MESSAGE_FAILED);
			}

			int responseMs = (int) (System.currentTimeMillis() - startedAt);
			aiLogRepository.save(new AiLog(userId, type.name(), AiLogState.OK.name(), responseMs,
					response.tokenCount(), null));
			return new AiCallResult(response.content());
		} finally {
			concurrencyLimiter.release();
			aiUsageService.release(userId, type);
		}
	}

	private String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
	}
}
