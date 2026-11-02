package com.growit.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 컨트롤러에서 던져진 예외를 {@link ErrorResponse} 모양의 JSON으로 바꾼다.
 * 도메인 서비스는 {@link ApiException}만 던지면 되고, 상태 코드·응답 모양을 직접 다루지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** 도메인 서비스가 던진 업무 규칙 위반을 {@link ApiException#getCode()}에 정의된 상태로 응답한다. */
	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		return ResponseEntity.status(e.getCode().getStatus())
				.body(new ErrorResponse(e.getCode().name(), e.getMessage()));
	}

	/** {@code @Valid} 검증 실패 시 첫 번째 필드 오류 메시지를 VALIDATION_ERROR로 응답한다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("입력값을 확인해 주세요");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), message));
	}

	/** 요청 본문이 JSON으로 읽히지 않을 때(깨진 JSON, 타입 불일치 등) VALIDATION_ERROR로 응답한다. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), "요청 형식이 올바르지 않아요"));
	}

	/** 경로·쿼리 파라미터가 선언된 타입으로 변환되지 않을 때(예: id에 문자열) VALIDATION_ERROR로 응답한다. */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), "요청 값이 올바르지 않아요"));
	}
}
