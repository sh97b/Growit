package com.growit.auth.controller;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입·로그인 주소. 둘 다 토큰 없이 호출한다({@link com.growit.auth.config.SecurityConfig}의 공개 경로).
 */
@Tag(name = "로그인", description = "회원가입·로그인")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** {@code POST /api/auth/signup} — 성공 시 201. 이메일 중복이면 EMAIL_TAKEN(409), 입력값 오류면 VALIDATION_ERROR(400). */
	@Operation(summary = "회원가입")
	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	/** {@code POST /api/auth/login} — 성공 시 200과 토큰. 이메일·비밀번호가 안 맞으면 UNAUTHORIZED(401). */
	@Operation(summary = "로그인")
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
