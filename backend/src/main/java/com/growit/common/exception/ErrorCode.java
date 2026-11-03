package com.growit.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	EMAIL_TAKEN(HttpStatus.CONFLICT),
	HOBBY_NAME_TAKEN(HttpStatus.CONFLICT),
	HOBBY_HAS_RECORDS(HttpStatus.CONFLICT),
	AI_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
	AI_BUSY(HttpStatus.SERVICE_UNAVAILABLE),
	AI_FAILED(HttpStatus.SERVICE_UNAVAILABLE),
	AI_DISABLED(HttpStatus.CONFLICT),
	ANALYSIS_NOT_READY(HttpStatus.CONFLICT);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
