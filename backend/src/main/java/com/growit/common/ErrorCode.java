package com.growit.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	HOBBY_NAME_TAKEN(HttpStatus.CONFLICT),
	HOBBY_HAS_RECORDS(HttpStatus.CONFLICT);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
