package com.growit.auth;

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
		String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

		assertThat(provider.parseUserId(tampered)).isEmpty();
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
