package com.growit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.ai.entity.AiResult;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.repository.AiResultRepository;
import com.growit.auth.security.CurrentUserProvider;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.auth.security.LoginAttemptLimiter;
import com.growit.auth.security.PasswordPolicy;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.notification.repository.NotificationSettingsRepository;
import com.growit.notification.repository.PushSubscriptionRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.Feeling;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.dto.ChangePasswordRequest;
import com.growit.user.dto.ChangePasswordResponse;
import com.growit.user.dto.DeleteAccountRequest;
import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DataJpaTest
class UserServiceTest {

	private static final String TEST_SECRET = "test-only-secret-key-please-replace-32bytes-min";
	private static final String TEST_IP = "203.0.113.1";
	private static final String CURRENT_PASSWORD = "Gr0wit-Journal9";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AiResultRepository aiResultRepository;

	@Autowired
	private AiLogRepository aiLogRepository;

	@Autowired
	private ActivityRecordRepository activityRecordRepository;

	@Autowired
	private HobbyRepository hobbyRepository;

	@Autowired
	private NotificationSettingsRepository notificationSettingsRepository;

	@Autowired
	private PushSubscriptionRepository pushSubscriptionRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final PasswordPolicy passwordPolicy = new PasswordPolicy();
	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 7);

	private Long createUser(String displayName) {
		User user = new User("growit_user@example.com", "hashed", displayName);
		return userRepository.save(user).getId();
	}

	private Long createUserWithPassword(String displayName, String rawPassword) {
		User user = new User("growit_user@example.com", passwordEncoder.encode(rawPassword), displayName);
		return userRepository.save(user).getId();
	}

	private UserService userService(Long currentUserId) {
		CurrentUserProvider currentUserProvider = () -> currentUserId;
		return new UserService(userRepository, currentUserProvider, passwordEncoder, passwordPolicy,
				new LoginAttemptLimiter(Clock.systemUTC()), jwtTokenProvider, aiResultRepository, aiLogRepository,
				activityRecordRepository, hobbyRepository, notificationSettingsRepository, pushSubscriptionRepository);
	}

	@Test
	void me_returnsOwnInfo() {
		Long userId = createUser("growit_user");

		MeResponse response = userService(userId).me();

		assertThat(response.email()).isEqualTo("growit_user@example.com");
		assertThat(response.displayName()).isEqualTo("growit_user");
		assertThat(response.aiEnabled()).isTrue();
	}

	@Test
	void updateMe_aiEnabledFalse_turnsOffAiForNewRecords() {
		Long userId = createUser("growit_user");

		MeResponse response = userService(userId).updateMe(new UpdateMeRequest("growit_user", false));

		assertThat(response.aiEnabled()).isFalse();
	}

	@Test
	void updateMe_aiEnabledOmitted_keepsExistingValue() {
		Long userId = createUser("growit_user");
		userService(userId).updateMe(new UpdateMeRequest("growit_user", false));

		MeResponse response = userService(userId).updateMe(new UpdateMeRequest("growit_user", null));

		assertThat(response.aiEnabled()).isFalse();
	}

	@Test
	void updateMe_trimsSurroundingWhitespace() {
		Long userId = createUser("growit_user");

		MeResponse response = userService(userId).updateMe(new UpdateMeRequest("  서현  ", null));

		assertThat(response.displayName()).isEqualTo("서현");
	}

	@Test
	void updateMe_blankName_throwsValidationError() {
		Long userId = createUser("growit_user");

		assertThatThrownBy(() -> userService(userId).updateMe(new UpdateMeRequest("   ", null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void updateMe_tooLongName_throwsValidationError() {
		Long userId = createUser("growit_user");
		String tooLong = "가".repeat(31);

		assertThatThrownBy(() -> userService(userId).updateMe(new UpdateMeRequest(tooLong, null)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void changePassword_correctCurrentPassword_updatesHashAndIncrementsTokenVersion() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		UserService service = userService(userId);

		ChangePasswordResponse response = service.changePassword(
				new ChangePasswordRequest(CURRENT_PASSWORD, "Another-Secure7"), TEST_IP);

		assertThat(response.accessToken()).isNotBlank();
		User reloaded = userRepository.findById(userId).orElseThrow();
		assertThat(passwordEncoder.matches("Another-Secure7", reloaded.getPasswordHash())).isTrue();
		assertThat(reloaded.getTokenVersion()).isEqualTo(1);
		assertThat(jwtTokenProvider.parseTokenVersion(response.accessToken())).isEqualTo(1);
	}

	@Test
	void changePassword_wrongCurrentPassword_throwsUnauthorized() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		UserService service = userService(userId);

		assertThatThrownBy(() -> service.changePassword(
				new ChangePasswordRequest("wrong-current-password", "Another-Secure7"), TEST_IP))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
	}

	@Test
	void changePassword_sameAsCurrentPassword_throwsValidationError() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		UserService service = userService(userId);

		assertThatThrownBy(() -> service.changePassword(
				new ChangePasswordRequest(CURRENT_PASSWORD, CURRENT_PASSWORD), TEST_IP))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void changePassword_newPasswordViolatesPolicy_throwsValidationError() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		UserService service = userService(userId);

		assertThatThrownBy(() -> service.changePassword(
				new ChangePasswordRequest(CURRENT_PASSWORD, "short"), TEST_IP))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	/** 현재 비밀번호 칸은 정책을 검사하지 않는다 - 옛 정책(8~72자, 한글 허용)으로 가입된 계정도 비밀번호를 바꿀 수 있어야 한다. */
	@Test
	void changePassword_legacyCurrentPasswordLongerThan20CharsWithKorean_updatesHash() {
		String legacyPassword = "그로잇비밀번호매우깁니다0123456789";
		Long userId = createUserWithPassword("growit_user", legacyPassword);
		UserService service = userService(userId);

		ChangePasswordResponse response = service.changePassword(
				new ChangePasswordRequest(legacyPassword, "Another-Secure7"), TEST_IP);

		assertThat(response.accessToken()).isNotBlank();
		User reloaded = userRepository.findById(userId).orElseThrow();
		assertThat(passwordEncoder.matches("Another-Secure7", reloaded.getPasswordHash())).isTrue();
	}

	@Test
	void changePassword_repeatedWrongCurrentPassword_eventuallyLocksLogin() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		UserService service = userService(userId);

		// checkAllowed는 이번 시도를 세기 전에 확인하므로, 잠금은 EMAIL_MAX_FAILURES번째 실패 "다음" 시도부터 걸린다.
		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES; i++) {
			assertThatThrownBy(() -> service.changePassword(
					new ChangePasswordRequest("wrong-current-password", "Another-Secure7"), TEST_IP))
					.isInstanceOfSatisfying(ApiException.class,
							e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
		}

		assertThatThrownBy(() -> service.changePassword(
				new ChangePasswordRequest("wrong-current-password", "Another-Secure7"), TEST_IP))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.LOGIN_LOCKED));
	}

	@Test
	void deleteAccount_correctPassword_removesAllOwnData() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		Long hobbyId = hobbyRepository.save(new Hobby(userId, "기타", "코드 전환")).getId();
		activityRecordRepository.save(
				new ActivityRecord(userId, hobbyId, LocalDate.of(2026, 1, 1), "연습했다", 20, null, Feeling.PROUD, false));
		aiResultRepository.save(new AiResult(userId, hobbyId, "COMPARE", LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 1), "{}", "[]", "cache-key-" + userId));

		userService(userId).deleteAccount(new DeleteAccountRequest(CURRENT_PASSWORD));

		assertThat(userRepository.findById(userId)).isEmpty();
		assertThat(userRepository.existsByEmail("growit_user@example.com")).isFalse();
		assertThat(hobbyRepository.findByUserIdOrderByIdAsc(userId)).isEmpty();
		assertThat(activityRecordRepository.findByUserId(userId)).isEmpty();
		assertThat(aiResultRepository.findByIdAndUserId(hobbyId, userId)).isEmpty();
	}

	@Test
	void deleteAccount_wrongPassword_throwsUnauthorizedAndKeepsData() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);

		assertThatThrownBy(() -> userService(userId).deleteAccount(new DeleteAccountRequest("wrong-password")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
		assertThat(userRepository.findById(userId)).isPresent();
	}

	@Test
	void deleteAccount_doesNotAffectAnotherUsersData() {
		Long userId = createUserWithPassword("growit_user", CURRENT_PASSWORD);
		User other = new User("someone_else@example.com", passwordEncoder.encode(CURRENT_PASSWORD), "other_user");
		Long otherId = userRepository.save(other).getId();
		Long otherHobbyId = hobbyRepository.save(new Hobby(otherId, "러닝", null)).getId();

		userService(userId).deleteAccount(new DeleteAccountRequest(CURRENT_PASSWORD));

		assertThat(userRepository.findById(otherId)).isPresent();
		assertThat(hobbyRepository.findById(otherHobbyId)).isPresent();
	}
}
