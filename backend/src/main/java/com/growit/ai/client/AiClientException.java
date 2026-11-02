package com.growit.ai.client;

/** 공급자 호출에서 생긴 문제를 키·요청 내용 없이 옮겨 담는다. */
public class AiClientException extends RuntimeException {

	public enum Type {
		RATE_LIMITED,
		SERVER_ERROR,
		TIMEOUT,
		INVALID_RESPONSE,
		UNKNOWN
	}

	private final Type type;

	public AiClientException(Type type, String message) {
		super(message);
		this.type = type;
	}

	public Type getType() {
		return type;
	}
}
