package com.growit.auth;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
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

@Tag(name = "로그인", description = "회원가입·로그인")
@SecurityRequirements
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@Operation(summary = "회원가입")
	@PostMapping("/signup")
	public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
	}

	@Operation(summary = "로그인")
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}
}
