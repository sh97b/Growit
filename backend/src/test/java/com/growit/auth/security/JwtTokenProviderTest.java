package com.growit.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final String SECRET = "test-only-secret-key-please-replace-32bytes-min";

	@Test
	void createAndParseToken_roundTrip() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, 7);

		String token = provider.createToken(42L);

		assertThat(provider.parseUserId(token)).contains(42L);
	}

	@Test
	void parseUserId_tamperedToken_returnsEmpty() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, 7);
		String token = provider.createToken(42L);

		assertThat(provider.parseUserId(tamperSignature(token))).isEmpty();
	}

	@Test
	void parseUserId_tamperedToken_returnsEmpty_repeated() {
		// 서명 끝 글자를 바꾸던 예전 방식은 base64url의 사용되지 않는 비트 때문에 같은 서명으로
		// 디코딩되는 경우가 있어 간헐적으로 실패했다(해시 전체는 동일 서명이라도 매번 바뀌므로
		// 20회 반복해 안정적으로 실패(=위조 거부)하는지 다시 확인한다).
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, 7);
		for (int i = 0; i < 20; i++) {
			String token = provider.createToken(42L);
			assertThat(provider.parseUserId(tamperSignature(token))).isEmpty();
		}
	}

	/** 서명 세그먼트의 첫 글자를 바꾼다. 첫 글자는 base64url에서 패딩·미사용 비트가 없어 항상 실제로 다른 바이트로 디코딩된다. */
	private static String tamperSignature(String token) {
		int lastDot = token.lastIndexOf('.');
		String head = token.substring(0, lastDot + 1);
		String signature = token.substring(lastDot + 1);
		char replacement = signature.charAt(0) == 'A' ? 'B' : 'A';
		return head + replacement + signature.substring(1);
	}

	@Test
	void parseUserId_expiredToken_returnsEmpty() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, -1);
		String token = provider.createToken(42L);

		assertThat(provider.parseUserId(token)).isEmpty();
	}

	@Test
	void construct_withSecretShorterThan32Bytes_throwsWeakKeyException() {
		assertThatThrownBy(() -> new JwtTokenProvider("too-short-secret", 7))
				.isInstanceOf(WeakKeyException.class);
	}
}
