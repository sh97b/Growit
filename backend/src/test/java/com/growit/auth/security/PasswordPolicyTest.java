package com.growit.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void validate_normalPassword_doesNotThrow() {
		assertThatCode(() -> policy.validate("Gr0wit-Journal9")).doesNotThrowAnyException();
	}

	@Test
	void validate_7Chars_throwsWithLengthMessage() {
		assertThatThrownBy(() -> policy.validate("Ab1!xy7"))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
					assertThat(e.getMessage()).isEqualTo("비밀번호는 8자 이상 20자 이하로 입력해 주세요");
				});
	}

	@Test
	void validate_8Chars_doesNotThrow() {
		assertThatCode(() -> policy.validate("Ab1!xy78")).doesNotThrowAnyException();
	}

	@Test
	void validate_20Chars_doesNotThrow() {
		String exactly20 = "Ab1!".repeat(5); // 20자

		assertThatCode(() -> policy.validate(exactly20)).doesNotThrowAnyException();
	}

	@Test
	void validate_21Chars_throwsWithLengthMessage() {
		String tooLong = "Ab1!".repeat(5) + "x"; // 21자

		assertThatThrownBy(() -> policy.validate(tooLong))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).isEqualTo("비밀번호는 8자 이상 20자 이하로 입력해 주세요"));
	}

	@Test
	void validate_disallowedCharacter_throwsWithCharsetMessage() {
		assertThatThrownBy(() -> policy.validate("가나다Abc1!xyz"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).contains("영문, 숫자, 특수문자").contains("만 쓸 수 있어요"));
	}

	@Test
	void validate_spaceCharacter_throwsWithCharsetMessage() {
		assertThatThrownBy(() -> policy.validate("Abc 123!xyz"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).contains("만 쓸 수 있어요"));
	}

	@Test
	void validate_noLetter_throwsWithLetterMessage() {
		assertThatThrownBy(() -> policy.validate("1234567!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).isEqualTo("영문을 하나 이상 포함해 주세요"));
	}

	@Test
	void validate_noDigit_throwsWithDigitMessage() {
		assertThatThrownBy(() -> policy.validate("Abcdefg!"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).isEqualTo("숫자를 하나 이상 포함해 주세요"));
	}

	@Test
	void validate_noSpecialChar_throwsWithSpecialCharMessage() {
		assertThatThrownBy(() -> policy.validate("Abcdefg1"))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getMessage()).contains("특수문자").contains("를 하나 이상 포함해 주세요"));
	}
}
