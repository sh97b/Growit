package com.growit.ai.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** COMPARE·COACH의 재사용 열쇠(cache_key)를 만드는 공통 규칙. 입력이 하나라도 바뀌면 열쇠가 달라진다. */
final class CacheKeys {

	private CacheKeys() {
	}

	static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없음", e);
		}
	}
}
