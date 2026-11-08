package com.growit.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	void validate_shorterThan8Chars_throwsWithLengthMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab1!xyz"))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					org.assertj.core.api.Assertions.assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
					org.assertj.core.api.Assertions.assertThat(e.getMessage())
							.isEqualTo("비밀번호는 8자 이상 64자 이하로 입력해 주세요");
				});
	}

	@Test
	void validate_longerThan64Chars_throwsWithLengthMessage() {
		String tooLong = "Ab1!".repeat(17); // 68자

		assertThatThrownBy(() -> policy.validate("user@example.com", tooLong))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("비밀번호는 8자 이상 64자 이하로 입력해 주세요"));
	}

	@Test
	void validate_64CharsExactly_doesNotThrow() {
		// ab1! 패턴 16회 반복(64자)이지만 반복 검사는 "같은 한 글자"만 보고, 연속 검사는 "한 방향 전체"만 보므로 통과한다.
		String exactly64 = "ab1!cd2@".repeat(8);

		assertThatCode(() -> policy.validate("someone_else@example.com", exactly64))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_disallowedCharacter_throwsWithCharsetMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "가나다Abc1!xyz"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("영문, 숫자, 특수문자").contains("만 쓸 수 있어요"));
	}

	@Test
	void validate_spaceCharacter_throwsWithCharsetMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Abc 123!xyz"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("만 쓸 수 있어요"));
	}

	@Test
	void validate_noLetter_throwsWithLetterMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "1234567!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("영문을 하나 이상 포함해 주세요"));
	}

	@Test
	void validate_noDigit_throwsWithDigitMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Abcdefg!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("숫자를 하나 이상 포함해 주세요"));
	}

	@Test
	void validate_noSpecialChar_throwsWithSpecialCharMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Abcdefg1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.contains("특수문자").contains("를 하나 이상 포함해 주세요"));
	}

	@Test
	void validate_passwordEqualsEmail_throwsWithEmailMessage() {
		assertThatThrownBy(() -> policy.validate("gr0witUs3r@example.com", "gr0witUs3r@example.com"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("이메일을 포함하지 않는 비밀번호를 써 주세요"));
	}

	@Test
	void validate_passwordContainsEmailLocalPart_throwsWithEmailMessage() {
		assertThatThrownBy(() -> policy.validate("growituser@example.com", "myGrowituserPass1!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("이메일을 포함하지 않는 비밀번호를 써 주세요"));
	}

	@Test
	void validate_shortEmailLocalPart_isNotCheckedForContainment() {
		// 로컬파트가 3자 미만이면(ab) 포함 검사를 하지 않는다 - 너무 흔한 조합이라 오탐이 많다.
		assertThatCode(() -> policy.validate("ab@example.com", "Gr0wit-Kyeong9"))
				.doesNotThrowAnyException();
	}

	@Test
	void validate_singleCharacterRepeated_throwsWithRepeatMessage() {
		// 영문·숫자·특수문자를 모두 포함하되, 끝에 같은 글자를 4번 이어 붙여 반복 검사 자체를 확인한다.
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab1!wwww"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("같은 문자를 반복하거나 연속된 문자만으로는 만들 수 없어요"));
	}

	@Test
	void validate_ascendingSequence_throwsWithRepeatMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab1!lmno"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("같은 문자를 반복하거나 연속된 문자만으로는 만들 수 없어요"));
	}

	@Test
	void validate_descendingSequence_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab1!srqp"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void validate_numericAscendingSequence_throwsValidationError() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Ab!12345"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("같은 문자를 반복하거나 연속된 문자만으로는 만들 수 없어요"));
	}

	@Test
	void validate_commonPassword_throwsWithBlocklistMessage() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Password123!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("많이 쓰이는 비밀번호예요. 다른 조합으로 바꿔 주세요"));
	}

	@Test
	void validate_commonPassword_isCaseInsensitive() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "PASSWORD123!"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void validate_commonPasswordVariant_withUppercaseAndDigitAndSpecial_isBlocked() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Welcome1!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("많이 쓰이는 비밀번호예요. 다른 조합으로 바꿔 주세요"));
	}

	@Test
	void validate_anotherCommonPasswordVariant_isBlocked() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Admin123!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> org.assertj.core.api.Assertions.assertThat(e.getMessage())
								.isEqualTo("많이 쓰이는 비밀번호예요. 다른 조합으로 바꿔 주세요"));
	}

	@Test
	void validate_qwerVariant_isBlocked() {
		assertThatThrownBy(() -> policy.validate("user@example.com", "Qwer1234!"))
				.isInstanceOf(ApiException.class);
	}
}
