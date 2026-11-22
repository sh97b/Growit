package com.growit.ai.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;

/**
 * OpenAI 쪽 문제(결제·한도 소진, 인증, 모델 접근)로 호출이 실패한 경우. 호출하는 쪽이 "잠시 쓸 수 없어요"로 따로 안내하려고
 * 일반 AI_FAILED와 구분한다(코드는 AI_FAILED 그대로라 기존 처리와 호환된다).
 */
public class AiProviderDownException extends ApiException {

	public AiProviderDownException(String message) {
		super(ErrorCode.AI_FAILED, message);
	}
}
