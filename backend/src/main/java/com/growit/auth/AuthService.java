package com.growit.auth;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final String LOGIN_FAILED_MESSAGE = "이메일 또는 비밀번호가 맞지 않아요";
	private static final int DISPLAY_NAME_MAX_LENGTH = 30;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
	}

	@Transactional
	public SignupResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ApiException(ErrorCode.EMAIL_TAKEN, "이미 사용 중인 이메일이에요");
		}
		String displayName = defaultDisplayName(email);
		User user = new User(email, passwordEncoder.encode(request.password()), displayName);
		userRepository.save(user);
		return new SignupResponse(user.getId(), user.getEmail());
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
		}
		return new LoginResponse(jwtTokenProvider.createToken(user.getId()));
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase();
	}

	private String defaultDisplayName(String email) {
		String localPart = email.substring(0, email.indexOf('@'));
		if (localPart.length() > DISPLAY_NAME_MAX_LENGTH) {
			return localPart.substring(0, DISPLAY_NAME_MAX_LENGTH);
		}
		return localPart;
	}
}
