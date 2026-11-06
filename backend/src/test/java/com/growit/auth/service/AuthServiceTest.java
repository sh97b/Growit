package com.growit.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
class AuthServiceTest {

	private static final String TEST_SECRET = "test-only-secret-key-please-replace-32bytes-min";

	@Autowired
	private UserRepository userRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 7);
	private final PasswordPolicy passwordPolicy = new PasswordPolicy();

	private AuthService authService() {
		return new AuthService(userRepository, passwordEncoder, jwtTokenProvider, passwordPolicy, 10);
	}

	@Test
	void signup_savesUserWithHashedPasswordAndDisplayName() {
		AuthService service = authService();

		SignupResponse response = service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal9"));

		assertThat(response.id()).isNotNull();
		assertThat(response.email()).isEqualTo("growit_user@example.com");
		User saved = userRepository.findById(response.id()).orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("Gr0wit-Journal9");
		assertThat(passwordEncoder.matches("Gr0wit-Journal9", saved.getPasswordHash())).isTrue();
		assertThat(saved.getDisplayName()).isEqualTo("growit_user");
	}

	@Test
	void signup_uppercaseEmail_normalizedToLowercase() {
		AuthService service = authService();

		SignupResponse response = service.signup(new SignupRequest("Growit_User@Example.com", "Gr0wit-Journal9"));

		assertThat(response.email()).isEqualTo("growit_user@example.com");
	}

	@Test
	void signup_duplicateEmail_throwsEmailTaken() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal9"));

		assertThatThrownBy(() -> service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal8")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.EMAIL_TAKEN));
	}

	@Test
	void login_correctPassword_returnsToken() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal9"));

		LoginResponse response = service.login(new LoginRequest("growit_user@example.com", "Gr0wit-Journal9"));

		assertThat(response.accessToken()).isNotBlank();
		assertThat(jwtTokenProvider.parseUserId(response.accessToken())).isPresent();
	}

	@Test
	void login_caseInsensitiveEmail_returnsToken() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal9"));

		LoginResponse response = service.login(new LoginRequest("Growit_User@Example.com", "Gr0wit-Journal9"));

		assertThat(response.accessToken()).isNotBlank();
	}

	@Test
	void login_wrongPassword_throwsUnauthorized() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user@example.com", "Gr0wit-Journal9"));

		assertThatThrownBy(() -> service.login(new LoginRequest("growit_user@example.com", "wrong-password")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	void login_unknownEmail_throwsUnauthorized() {
		AuthService service = authService();

		assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "Gr0wit-Journal9")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	void login_hashCostLowerThanConfigured_rehashesWithNewCost() {
		PasswordEncoder lowCostEncoder = new BCryptPasswordEncoder(4);
		User user = new User("growit_user@example.com", lowCostEncoder.encode("Gr0wit-Journal9"), "growit_user");
		userRepository.save(user);
		String oldHash = user.getPasswordHash();
		AuthService service = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, passwordPolicy, 10);

		service.login(new LoginRequest("growit_user@example.com", "Gr0wit-Journal9"));

		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getPasswordHash()).isNotEqualTo(oldHash);
		assertThat(passwordEncoder.matches("Gr0wit-Journal9", reloaded.getPasswordHash())).isTrue();
	}
}
