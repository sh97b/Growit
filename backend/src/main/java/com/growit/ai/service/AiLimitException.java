package com.growit.ai.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;

/**
 * 하루 한도에 걸려 AI 호출을 건너뛴 경우. 같은 AI_BUSY여도 동시 호출 제한·공급자 429와 구분하려고 따로 둔다
 * (호출하는 쪽이 "한도 때문에 못 했다"를 사용자에게 따로 알린다).
 */
public class AiLimitException extends ApiException {

	private final boolean global;

	public AiLimitException(ErrorCode code, String message, boolean global) {
		super(code, message);
		this.global = global;
	}

	public boolean isGlobal() {
		return global;
	}
}
