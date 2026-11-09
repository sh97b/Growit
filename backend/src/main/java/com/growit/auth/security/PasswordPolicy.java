package com.growit.auth.security;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 가입과 비밀번호 변경이 함께 쓰는 비밀번호 규칙. ASCII 문자만 허용하므로 글자 수와 바이트 수가 같아,
 * BCrypt의 72바이트 제한 대신 20자 상한만으로 충분하다. 기존 계정은 로그인에서 이 규칙을 검사하지 않는다
 * (가입 당시 규칙이 달랐을 수 있어도 그대로 로그인할 수 있어야 한다).
 */
@Component
public class PasswordPolicy {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 20;
	private static final String SPECIAL_CHARS = "! @ # $ % ^ & * ( ) - _ = + . , ?";
	private static final Pattern ALLOWED_CHARS = Pattern.compile("^[A-Za-z0-9!@#$%^&*()\\-_=+.,?]*$");
	private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
	private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
	private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()\\-_=+.,?]");

	/** 검사 순서가 사용자에게 보여 주는 안내 순서와 같다 - 한 번에 하나씩, 가장 먼저 걸리는 이유만 알려 준다. */
	public void validate(String rawPassword) {
		if (rawPassword == null || rawPassword.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호를 입력해 주세요");
		}

		int length = rawPassword.length();
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호는 8자 이상 20자 이하로 입력해 주세요");
		}
		if (!ALLOWED_CHARS.matcher(rawPassword).matches()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"영문, 숫자, 특수문자(" + SPECIAL_CHARS + ")만 쓸 수 있어요");
		}
		if (!HAS_LETTER.matcher(rawPassword).find()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "영문을 하나 이상 포함해 주세요");
		}
		if (!HAS_DIGIT.matcher(rawPassword).find()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "숫자를 하나 이상 포함해 주세요");
		}
		if (!HAS_SPECIAL.matcher(rawPassword).find()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"특수문자(" + SPECIAL_CHARS + ")를 하나 이상 포함해 주세요");
		}
	}
}
