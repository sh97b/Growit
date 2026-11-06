package com.growit.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void validate_normalPassword_doesNotThrow() {
		assertThatCode(() -> policy.validate("growit_user@example.com", "Gr0wit-Journal9"))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_shorterThan8Chars_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab1!xyz"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
								.isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void validate_longerThan72Bytes_throwsValidationErrorWithLengthMessage() {
		String longPassword = "a".repeat(73);

		assertThatThrownBy(() -> policy.validate("user@example.com", longPassword))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("비밀번호가 너무 길어요"));
	}

	@Test
	void validate_72BytesExactly_doesNotThrow() {
		// ab를 반복해 72바이트를 만들되, 반복·연속·흔한 비밀번호 규칙에 걸리지 않게 한다.
		String exactly72Bytes = "ab1".repeat(24);

		assertThatCode(() -> policy.validate("someone_else@example.com", exactly72Bytes))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_multibyteCharactersCountedByUtf8Bytes_throwsWhenOverLimit() {
		// 한글 한 글자는 UTF-8로 3바이트라 25자(75바이트)면 72바이트를 넘는다.
		String korean25Chars = "가".repeat(25);

		assertThatThrownBy(() -> policy.validate("user@example.com", korean25Chars))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("비밀번호가 너무 길어요"));
	}

	@Test
	void validate_commonPassword_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "password123"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("흔한 비밀번호"));
	}

	@Test
	void validate_commonPassword_isCaseInsensitive() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "PASSWORD123"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void validate_koreanKeyboardTypedAsEnglish_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "dkssud123"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void validate_passwordEqualsEmail_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("growit_user@example.com", "growit_user@example.com"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("이메일과 같은"));
	}

	@Test
	void validate_passwordContainsEmailLocalPart_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("growituser@example.com", "myGrowituserPass1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("이메일을 포함하지"));
	}

	@Test
	void validate_shortEmailLocalPart_isNotCheckedForContainment() {
		// 로컬파트가 3자 미만이면(ab) 포함 검사를 하지 않는다 - 너무 흔한 조합이라 오탐이 많다.
		assertThatCode(() -> policy.validate("ab@example.com", "Gr0wit-Abcdefg9"))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_singleCharacterRepeated_throwsValidationError() {
		// 블록 목록에 없는 반복 문자를 써서, 블록 목록이 아니라 반복 검사가 실제로 걸리는지 확인한다.
		assertThatThrownBy(() -> policy.validate("user@example.com", "wwwwwwww"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("같은 글자를 반복"));
	}

	@Test
	void validate_ascendingSequence_throwsValidationError() {
		// 블록 목록에 없는 연속 문자열을 써서, 블록 목록이 아니라 연속 검사가 실제로 걸리는지 확인한다.
		assertThatThrownBy(() -> policy.validate("user@example.com", "lmnopqrs"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("순서대로 이어지는"));
	}

	@Test
	void validate_descendingSequence_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "srqponml"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void validate_numericAscendingSequence_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "01234567"))
				.isInstanceOf(ApiException.class);
	}
}
