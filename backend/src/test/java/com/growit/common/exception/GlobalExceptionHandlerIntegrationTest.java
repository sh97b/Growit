package com.growit.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.auth.security.JwtTokenProvider;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * MockMvc는 실제 서블릿 컨테이너의 ERROR 디스패치를 재현하지 않아, 처리되지 않은 예외가 보안 필터에
 * 막혀 401로 둔갑하는 사고(2026-11-13, 푸시 전송 중 NoClassDefFoundError)를 잡지 못했다. 실제 포트로
 * 띄워 진짜 HTTP 요청을 보내야 이 경로를 검증할 수 있다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class GlobalExceptionHandlerIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private UserRepository userRepository;

	@Test
	void unhandledException_returnsInternalErrorNotUnauthorized() {
		User user = userRepository.save(new User("throw_test@example.com", "hash", "회원"));
		String token = jwtTokenProvider.createToken(user.getId(), user.getTokenVersion());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<ErrorResponse> response = restTemplate.exchange("/api/test/throw",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void unhandledError_stillReaches500NotUnauthorized() {
		// Error는 GlobalExceptionHandler(Exception만 잡음)를 통과해 서블릿의 /error ERROR 디스패치로
		// 가므로, 실제로 검증하려는 것은 그 디스패치가 SecurityConfig에서 인증 없이 통과하는지다.
		User user = userRepository.save(new User("throw_error_test@example.com", "hash", "회원"));
		String token = jwtTokenProvider.createToken(user.getId(), user.getTokenVersion());

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<String> response = restTemplate.exchange("/api/test/throw-error",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@Test
	void noToken_stillReturnsTokenInvalidUnauthorized() {
		ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/api/test/throw", ErrorResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().code()).isEqualTo("TOKEN_INVALID");
	}
}
