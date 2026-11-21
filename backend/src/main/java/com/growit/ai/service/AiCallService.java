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
	/** 로드맵 JSON은 길어서 환경변수(AI_MAX_OUTPUT_TOKENS)가 작게 고정돼 있어도 이 값 아래로 내려가지 않게 한다. */
	static final int ROADMAP_MIN_OUTPUT_TOKENS = 1500;

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
		return call(userId, type, systemPrompt, userData, false);
	}

	/**
	 * retry가 true면 같은 요청의 교정 재호출이다: 하루 한도 확인·예약을 건너뛰고 로그를 RETRY로 남겨 한도에 세지 않는다
	 * (처음 호출이 이미 한 번 셌다). 동시 호출 제한은 그대로 적용한다.
	 */
	public AiCallResult call(Long userId, AiResultType type, String systemPrompt, Object userData, boolean retry) {
		if (!properties.isEnabled()) {
			throw new ApiException(ErrorCode.AI_FAILED, MESSAGE_FAILED);
		}

		if (!retry) {
			aiUsageService.reserve(userId, type);
		}

		if (!concurrencyLimiter.tryAcquire()) {
			if (!retry) {
				aiUsageService.release(userId, type);
			}
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
				response = type == AiResultType.ROADMAP
						? aiChatClient.complete(safeSystemPrompt, userContent, ROADMAP_MIN_OUTPUT_TOKENS)
						: aiChatClient.complete(safeSystemPrompt, userContent);
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
			aiLogRepository.save(new AiLog(userId, type.name(), (retry ? AiLogState.RETRY : AiLogState.OK).name(), responseMs,
					response.tokenCount(), null));
			return new AiCallResult(response.content(), response.model());
		} finally {
			concurrencyLimiter.release();
			if (!retry) {
				aiUsageService.release(userId, type);
			}
		}
	}

	private String truncate(String message) {
		if (message == null) {
			return null;
		}
		return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
	}
}
