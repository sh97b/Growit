package com.growit.common.exception;

public class ApiException extends RuntimeException {

	private final ErrorCode code;
	private final Long retryAfterSeconds;

	public ApiException(ErrorCode code, String message) {
		this(code, message, null);
	}

	/** retryAfterSeconds가 있으면 Retry-After 헤더로 함께 내려간다({@link GlobalExceptionHandler}). 로그인 잠금 등에 쓴다. */
	public ApiException(ErrorCode code, String message, Long retryAfterSeconds) {
		super(message);
		this.code = code;
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public ErrorCode getCode() {
		return code;
	}

	public Long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
