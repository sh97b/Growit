package com.growit.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 오류 응답의 {@code code} 값과 HTTP 상태를 함께 묶은 목록.
 * <p>{@link GlobalExceptionHandler}와 각 도메인 서비스가 {@link ApiException}을 던질 때 이 중 하나를 고른다.
 */
public enum ErrorCode {

	/** 400. 입력값 검증 실패(형식 오류, 빈 값, 범위 초과 등). */
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),

	/** 401. 로그인이 필요하거나 토큰이 없음·위조·만료됨. */
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

	/** 404. 자원이 없거나 요청한 사용자의 것이 아님(남의 데이터는 존재 여부를 숨기려고 403이 아닌 404로 응답). */
	NOT_FOUND(HttpStatus.NOT_FOUND),

	/** 409. 가입하려는 이메일이 이미 쓰이고 있음. */
	EMAIL_TAKEN(HttpStatus.CONFLICT),

	/** 409. 같은 사용자 안에서 활동 분야 이름이 중복됨. */
	HOBBY_NAME_TAKEN(HttpStatus.CONFLICT),

	/** 409. 기록이 남아 있는 활동 분야를 삭제하려고 함. */
	HOBBY_HAS_RECORDS(HttpStatus.CONFLICT);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
