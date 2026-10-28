package com.growit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.auth.dto.LoginRequest;
import com.growit.auth.dto.LoginResponse;
import com.growit.auth.dto.SignupRequest;
import com.growit.auth.dto.SignupResponse;
import com.growit.common.ApiException;
import com.growit.common.ErrorCode;
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

	private AuthService authService() {
		return new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
	}

	@Test
	void signup_savesUserWithHashedPassword() {
		AuthService service = authService();

		SignupResponse response = service.signup(new SignupRequest("growit_user", "password1234"));

		assertThat(response.id()).isNotNull();
		assertThat(response.username()).isEqualTo("growit_user");
		User saved = userRepository.findById(response.id()).orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("password1234");
		assertThat(passwordEncoder.matches("password1234", saved.getPasswordHash())).isTrue();
	}

	@Test
	void signup_duplicateUsername_throwsUsernameTaken() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user", "password1234"));

		assertThatThrownBy(() -> service.signup(new SignupRequest("growit_user", "password5678")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.USERNAME_TAKEN));
	}

	@Test
	void login_correctPassword_returnsToken() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user", "password1234"));

		LoginResponse response = service.login(new LoginRequest("growit_user", "password1234"));

		assertThat(response.accessToken()).isNotBlank();
		assertThat(jwtTokenProvider.parseUserId(response.accessToken())).isPresent();
	}

	@Test
	void login_wrongPassword_throwsUnauthorized() {
		AuthService service = authService();
		service.signup(new SignupRequest("growit_user", "password1234"));

		assertThatThrownBy(() -> service.login(new LoginRequest("growit_user", "wrong-password")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	void login_unknownUsername_throwsUnauthorized() {
		AuthService service = authService();

		assertThatThrownBy(() -> service.login(new LoginRequest("nobody", "password1234")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}
}
