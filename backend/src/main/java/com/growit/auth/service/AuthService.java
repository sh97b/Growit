package com.growit.auth.service;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.auth.security.PasswordPolicy;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
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
	private final PasswordPolicy passwordPolicy;
	private final int bcryptCost;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider, PasswordPolicy passwordPolicy,
			@Value("${app.password.bcrypt-cost:10}") int bcryptCost) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.passwordPolicy = passwordPolicy;
		this.bcryptCost = bcryptCost;
	}

	/** 이메일은 대소문자만 다른 중복 가입을 막으려고 소문자로 통일해 저장하고, 이름은 입력받지 않아 이메일 앞부분을 기본값으로 쓴다. */
	@Transactional
	public SignupResponse signup(SignupRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new ApiException(ErrorCode.EMAIL_TAKEN, "이미 사용 중인 이메일이에요");
		}
		passwordPolicy.validate(email, request.password());
		String displayName = defaultDisplayName(email);
		User user = new User(email, passwordEncoder.encode(request.password()), displayName);
		userRepository.save(user);
		return new SignupResponse(user.getId(), user.getEmail());
	}

	/**
	 * 이메일이 없을 때와 비밀번호가 틀렸을 때 같은 메시지로 실패해서, 어느 쪽이 틀렸는지 알려주지 않는다.
	 * 쓰기(비용이 낮은 해시 재저장)가 있을 수 있어 읽기 전용 트랜잭션으로 두지 않는다.
	 */
	@Transactional
	public LoginResponse login(LoginRequest request) {
		String email = normalizeEmail(request.email());
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE));
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
		}
		rehashIfCostIsLow(user, request.password());
		return new LoginResponse(jwtTokenProvider.createToken(user.getId()));
	}

	/** PASSWORD_BCRYPT_COST를 올린 뒤에도 기존 사용자의 해시 비용이 낮은 채로 남지 않도록, 로그인할 때마다 확인해 다시 저장한다. */
	private void rehashIfCostIsLow(User user, String rawPassword) {
		int currentCost = bcryptCostOf(user.getPasswordHash());
		if (currentCost >= 0 && currentCost < bcryptCost) {
			user.setPasswordHash(passwordEncoder.encode(rawPassword));
		}
	}

	private int bcryptCostOf(String hash) {
		String[] parts = hash.split("\\$");
		if (parts.length < 3) {
			return -1;
		}
		try {
			return Integer.parseInt(parts[2]);
		} catch (NumberFormatException e) {
			return -1;
		}
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
