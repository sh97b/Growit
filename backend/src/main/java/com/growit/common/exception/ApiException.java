package com.growit.common.exception;

/**
 * 도메인 서비스가 업무 규칙 위반을 알릴 때 던지는 예외.
 * <p>{@link GlobalExceptionHandler}가 잡아서 {@link #getCode()}에 맞는 HTTP 상태와
 * {@link ErrorResponse}로 바꾼다. 컨트롤러는 이 예외를 직접 잡지 않는다.
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	public ApiException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode getCode() {
		return code;
	}
}
