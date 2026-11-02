package com.growit.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserServiceTest {

	@Autowired
	private UserRepository userRepository;

	private Long createUser(String displayName) {
		User user = new User("growit_user@example.com", "hashed", displayName);
		return userRepository.save(user).getId();
	}

	private UserService userService(Long currentUserId) {
		CurrentUserProvider currentUserProvider = () -> currentUserId;
		return new UserService(userRepository, currentUserProvider);
	}

	@Test
	void me_returnsOwnInfo() {
		Long userId = createUser("growit_user");

		MeResponse response = userService(userId).me();

		assertThat(response.email()).isEqualTo("growit_user@example.com");
		assertThat(response.displayName()).isEqualTo("growit_user");
	}

	@Test
	void updateMe_trimsSurroundingWhitespace() {
		Long userId = createUser("growit_user");

		MeResponse response = userService(userId).updateMe(new UpdateMeRequest("  서현  "));

		assertThat(response.displayName()).isEqualTo("서현");
	}

	@Test
	void updateMe_blankName_throwsValidationError() {
		Long userId = createUser("growit_user");

		assertThatThrownBy(() -> userService(userId).updateMe(new UpdateMeRequest("   ")))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void updateMe_tooLongName_throwsValidationError() {
		Long userId = createUser("growit_user");
		String tooLong = "가".repeat(31);

		assertThatThrownBy(() -> userService(userId).updateMe(new UpdateMeRequest(tooLong)))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}
}
