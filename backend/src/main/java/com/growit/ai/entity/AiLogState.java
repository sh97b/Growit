package com.growit.ai.entity;

public enum AiLogState {
	OK,
	FAIL,
	LIMIT,
	/** 검증 실패 뒤 교정 지시로 다시 부른 호출. 하루 한도(OK 집계)에 세지 않는다. */
	RETRY
}
