package com.growit.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
	PASSWORD_MISMATCH(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	EMAIL_TAKEN(HttpStatus.CONFLICT),
	HOBBY_NAME_TAKEN(HttpStatus.CONFLICT),
	HOBBY_HAS_RECORDS(HttpStatus.CONFLICT),
	AI_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
	AI_BUSY(HttpStatus.SERVICE_UNAVAILABLE),
	AI_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
	AI_DISABLED(HttpStatus.CONFLICT),
	ANALYSIS_NOT_READY(HttpStatus.CONFLICT),
	AI_ALREADY_SIMPLIFIED(HttpStatus.CONFLICT),
	LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS),
	PUSH_DISABLED(HttpStatus.CONFLICT),
	PUSH_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
	SOCIAL_ACCOUNT(HttpStatus.CONFLICT),
	OAUTH_STATE_INVALID(HttpStatus.UNAUTHORIZED),
	OAUTH_CODE_INVALID(HttpStatus.UNAUTHORIZED);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
