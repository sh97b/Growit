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
import org.springframework.stereotype.Component;

/**
 * 가입과 비밀번호 변경이 함께 쓰는 비밀번호 규칙. 최대 길이를 바이트로 재는 이유는 BCrypt가 72바이트까지만
 * 읽어서, 그보다 길면 뒷부분이 조용히 잘린 채로 저장되기 때문이다(길게 썼다고 믿는 부분이 실제로는 검증에
 * 안 쓰이는 보안 문제로 이어질 수 있음).
 */
@Component
public class PasswordPolicy {

	private static final String BLOCKLIST_RESOURCE = "password-blocklist.txt";
	private static final int MIN_LENGTH = 8;
	private static final int MAX_BYTES = 72;
	private static final int MIN_EMAIL_LOCAL_PART_TO_CHECK = 3;

	private final Set<String> blocklist = loadBlocklist();

	/** 이메일 로컬파트 포함·완전 일치·반복/연속 문자는 각각 다른 메시지로 거절한다(왜 막혔는지 알 수 있게). */
	public void validate(String email, String rawPassword) {
		if (rawPassword == null || rawPassword.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호를 입력해 주세요");
		}

		int charCount = rawPassword.codePointCount(0, rawPassword.length());
		if (charCount < MIN_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "비밀번호는 8자 이상으로 입력해 주세요");
		}
		int byteLength = rawPassword.getBytes(StandardCharsets.UTF_8).length;
		if (byteLength > MAX_BYTES) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"비밀번호가 너무 길어요(영문 기준 72자, 한글은 약 24자까지예요)");
		}

		String lower = rawPassword.toLowerCase();
		if (blocklist.contains(lower)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "너무 흔한 비밀번호예요. 다른 비밀번호를 써 주세요");
		}

		String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
		int atIndex = normalizedEmail.indexOf('@');
		String localPart = atIndex > 0 ? normalizedEmail.substring(0, atIndex) : "";
		if (!normalizedEmail.isEmpty() && lower.equals(normalizedEmail)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이메일과 같은 비밀번호는 쓸 수 없어요");
		}
		if (localPart.length() >= MIN_EMAIL_LOCAL_PART_TO_CHECK && lower.contains(localPart)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이메일을 포함하지 않는 비밀번호를 써 주세요");
		}

		if (isSingleCharacterRepeated(rawPassword)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "같은 글자를 반복한 비밀번호는 쓸 수 없어요");
		}
		if (isSimpleSequence(lower)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "순서대로 이어지는 비밀번호는 쓸 수 없어요");
		}
	}

	private boolean isSingleCharacterRepeated(String password) {
		char first = password.charAt(0);
		for (int i = 1; i < password.length(); i++) {
			if (password.charAt(i) != first) {
				return false;
			}
		}
		return true;
	}

	/** 전체가 "12345678"처럼 한 방향으로 쭉 이어지는 경우만 막는다(일부만 연속인 건 통과시킨다). */
	private boolean isSimpleSequence(String password) {
		if (password.length() < 2) {
			return false;
		}
		boolean ascending = true;
		boolean descending = true;
		for (int i = 1; i < password.length(); i++) {
			int diff = password.charAt(i) - password.charAt(i - 1);
			if (diff != 1) {
				ascending = false;
			}
			if (diff != -1) {
				descending = false;
			}
		}
		return ascending || descending;
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
