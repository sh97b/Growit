package com.growit.auth.controller;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.growit.common.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로그인", description = "회원가입·로그인")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** 이메일이 이미 쓰이고 있으면 EMAIL_TAKEN(409). */
	@Operation(summary = "회원가입")
	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	/**
	 * 이메일이 없거나 비밀번호가 틀려도 같은 UNAUTHORIZED(401)로 응답해 계정 존재 여부를 숨긴다.
	 * 같은 이메일로 10분 안에 5번, 같은 IP로 10분 안에 30번 실패하면 LOGIN_LOCKED(429, Retry-After 포함)로 막는다.
	 */
	@Operation(summary = "로그인")
	@ApiResponse(responseCode = "429", description = "LOGIN_LOCKED: 로그인 시도가 너무 많음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		return authService.login(request, httpRequest.getRemoteAddr());
	}
}
