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

	private static final String LOGIN_FAILED_MESSAGE = "아이디 또는 비밀번호가 맞지 않아요";

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
		if (userRepository.existsByUsername(request.username())) {
			throw new ApiException(ErrorCode.USERNAME_TAKEN, "이미 사용 중인 아이디예요");
		}
		User user = new User(request.username(), passwordEncoder.encode(request.password()));
		userRepository.save(user);
		return new SignupResponse(user.getId(), user.getUsername());
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username())
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
		}
		return new LoginResponse(jwtTokenProvider.createToken(user.getId()));
	}
}
