package com.growit.ai.client;

/** 공급자 호출에서 생긴 문제를 키·요청 내용 없이 옮겨 담는다. */
public class AiClientException extends RuntimeException {

	public enum Type {
		RATE_LIMITED,
		/** 한도 소진(quota)·인증(401/403)·모델 접근 문제처럼 서비스 쪽 조치가 필요한 공급자 오류. */
		PROVIDER_DOWN,
		SERVER_ERROR,
		TIMEOUT,
		INVALID_RESPONSE,
		UNKNOWN
	}

	private final Type type;
	private final String sub;

	public AiClientException(Type type, String message) {
		this(type, message, null);
	}

	/** sub는 공급자가 준 오류 코드(예: insufficient_quota, http_401). 요청 내용·키는 담지 않는다. */
	public AiClientException(Type type, String message, String sub) {
		super(message);
		this.type = type;
		this.sub = sub;
	}

	public String getSub() {
		return sub;
	}

	public Type getType() {
		return type;
	}
}
