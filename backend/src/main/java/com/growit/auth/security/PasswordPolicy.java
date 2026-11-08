package com.growit.auth.security;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 가입과 비밀번호 변경이 함께 쓰는 비밀번호 규칙. ASCII 문자만 허용하므로 글자 수와 바이트 수가 같아,
 * BCrypt의 72바이트 제한 대신 64자 상한만으로 충분하다. 기존 계정은 로그인에서 이 규칙을 검사하지 않는다
 * (가입 당시 규칙이 달랐을 수 있어도 그대로 로그인할 수 있어야 한다).
 */
@Component
public class PasswordPolicy {

	private static final String BLOCKLIST_RESOURCE = "password-blocklist.txt";
	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 64;
	private static final int MIN_EMAIL_LOCAL_PART_TO_CHECK = 3;
	private static final String SPECIAL_CHARS = "! @ # $ % ^ & * ( ) - _ = + . , ?";
	private static final Pattern ALLOWED_CHARS = Pattern.compile("^[A-Za-z0-9!@#$%^&*()\\-_=+.,?]*$");
	private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");
	private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");
	private static final Pattern HAS_SPECIAL = Pattern.compile("[!@#$%^&*()\\-_=+.,?]");

	private final Set<String> blocklist = loadBlocklist();

	/** 검사 순서가 사용자에게 보여 주는 안내 순서와 같다 - 한 번에 하나씩, 가장 먼저 걸리는 이유만 알려 준다. */
	public void validate(String email, String rawPassword) {
		if (rawPassword == null || rawPassword.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호를 입력해 주세요");
		}

		int length = rawPassword.length();
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호는 8자 이상 64자 이하로 입력해 주세요");
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

		String lower = rawPassword.toLowerCase();
		String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
		int atIndex = normalizedEmail.indexOf('@');
		String localPart = atIndex > 0 ? normalizedEmail.substring(0, atIndex) : "";
		boolean containsEmail = (!normalizedEmail.isEmpty() && lower.equals(normalizedEmail))
				|| (localPart.length() >= MIN_EMAIL_LOCAL_PART_TO_CHECK && lower.contains(localPart));
		if (containsEmail) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이메일을 포함하지 않는 비밀번호를 써 주세요");
		}

		if (hasRepeatedOrSequentialRun(lower)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"같은 문자를 반복하거나 연속된 문자만으로는 만들 수 없어요");
		}

		if (blocklist.contains(lower)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "많이 쓰이는 비밀번호예요. 다른 조합으로 바꿔 주세요");
		}
	}

	private static final int RUN_THRESHOLD = 4;

	/**
	 * 같은 글자가 4번 이상 이어지거나("wwww"), 4글자 이상이 한 방향으로 연속("1234", "abcd")이면 막는다.
	 * 전체 문자열이 아니라 부분 문자열 기준이라, 영문·숫자·특수문자를 고루 섞어도 그 안에 숨은 패턴을 잡아낸다.
	 */
	private boolean hasRepeatedOrSequentialRun(String lowerPassword) {
		int repeatRun = 1;
		int ascRun = 1;
		int descRun = 1;
		for (int i = 1; i < lowerPassword.length(); i++) {
			int diff = lowerPassword.charAt(i) - lowerPassword.charAt(i - 1);
			repeatRun = diff == 0 ? repeatRun + 1 : 1;
			ascRun = diff == 1 ? ascRun + 1 : 1;
			descRun = diff == -1 ? descRun + 1 : 1;
			if (repeatRun >= RUN_THRESHOLD || ascRun >= RUN_THRESHOLD || descRun >= RUN_THRESHOLD) {
				return true;
			}
		}
		return false;
	}

	/** 완전한 목록이 아니다 - 알려진 흔한 비밀번호·키보드 패턴·한글 자판을 영문으로 친 흔한 입력만 모아 둔 것이다. */
	private static Set<String> loadBlocklist() {
		Set<String> words = new HashSet<>();
		try (InputStream in = PasswordPolicy.class.getClassLoader().getResourceAsStream(BLOCKLIST_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException(BLOCKLIST_RESOURCE + "를 찾을 수 없음");
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#")) {
						continue;
					}
					words.add(trimmed.toLowerCase());
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException(BLOCKLIST_RESOURCE + "를 읽지 못함", e);
		}
		return words;
	}
}
