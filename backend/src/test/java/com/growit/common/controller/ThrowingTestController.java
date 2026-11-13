package com.growit.common.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 소스에만 존재한다(운영 코드에는 없음). 처리되지 않은 예외가 401이 아니라 500으로 나가는지
 * 실제 서블릿 에러 디스패치로 확인하려면 일부러 예외를 던지는 엔드포인트가 필요해서 만들었다.
 */
@RestController
class ThrowingTestController {

	@GetMapping("/api/test/throw")
	public String throwUnhandledException() {
		throw new RuntimeException("GlobalExceptionHandlerIntegrationTest 전용 — 의도적으로 던진 예외");
	}

	/**
	 * NoClassDefFoundError 같은 {@link Error}는 {@code @ExceptionHandler(Exception.class)}로 못
	 * 잡는다(Error는 Exception이 아니다) — 서블릿 컨테이너가 /error로 ERROR 디스패치를 만드는 경로를
	 * 그대로 타므로, SecurityConfig가 그 디스패치를 막지 않는지는 이 엔드포인트로만 확인할 수 있다.
	 */
	@GetMapping("/api/test/throw-error")
	public String throwUnhandledError() {
		throw new AssertionError("GlobalExceptionHandlerIntegrationTest 전용 — 의도적으로 던진 Error");
	}
}
