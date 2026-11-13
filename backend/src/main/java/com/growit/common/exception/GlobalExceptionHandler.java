package com.growit.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 어떤 예외가 나든 {@code {code, message}} 한 모양으로 바꿔 응답한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.getCode().getStatus());
		if (e.getRetryAfterSeconds() != null) {
			builder.header("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
		}
		return builder.body(new ErrorResponse(e.getCode().name(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(error -> error.getDefaultMessage())
				.orElse("입력값을 확인해 주세요");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), message));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), "요청 형식이 올바르지 않아요"));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), "요청 값이 올바르지 않아요"));
	}

	/**
	 * 정적 자원(Swagger UI 등)을 못 찾았을 때 Spring이 이미 404로 처리하던 것을, 아래의 모든 예외를
	 * 잡는 안전망이 500으로 덮어쓰지 않게 먼저 처리한다(Swagger 꺼짐 상태에서 /swagger-ui.html이
	 * 404가 아니라 500이 됐던 회귀를 여기서 막는다).
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new ErrorResponse(ErrorCode.NOT_FOUND.name(), "요청한 자원을 찾을 수 없어요"));
	}

	/**
	 * 마지막 안전망. 처리되지 않은 예외가 401로 둔갑해 로그인 상태를 끊는 사고(푸시 전송 중
	 * NoClassDefFoundError로 발생)가 있었던 뒤 추가했다 — 어떤 예외든 500으로 끝나야 한다.
	 * 스택은 서버 로그에만 남기고(요청 본문·토큰·사용자 글은 찍지 않는다) 응답에는 고정 문구만 준다.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "잠시 후 다시 시도해 주세요"));
	}
}
