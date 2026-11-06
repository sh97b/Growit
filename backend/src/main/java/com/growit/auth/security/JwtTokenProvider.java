package com.growit.auth.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 토큰에는 사용자 id와 토큰 버전(tv)만 담아 개인정보 노출을 최소화한다. 유효기간은 {@code app.jwt.expiration-days}.
 * JWT_SECRET이 없거나 32바이트(HS256 최소 길이)보다 짧으면 약한 키로 서명하지 않도록 서버 시작을 실패시킨다.
 */
@Component
public class JwtTokenProvider {

	private static final String TOKEN_VERSION_CLAIM = "tv";

	private final SecretKey key;
	private final Duration expiration;

	public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-days}") long expirationDays) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofDays(expirationDays);
	}

	/** tv 클레임이 없는 예전 토큰은 0으로 본다({@link #parseTokenVersion}도 같은 기본값을 쓴다). */
	public String createToken(Long userId) {
		return createToken(userId, 0);
	}

	public String createToken(Long userId, int tokenVersion) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(TOKEN_VERSION_CLAIM, tokenVersion)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	public Optional<Long> parseUserId(String token) {
		try {
			String subject = Jwts.parser().verifyWith(key).build()
					.parseSignedClaims(token).getPayload().getSubject();
			return Optional.of(Long.parseLong(subject));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** 서버 배포 전 발급된 토큰처럼 tv 클레임이 없으면 0으로 간주해, 배포 순간 로그인해 있던 테스터가 끊기지 않게 한다. */
	public int parseTokenVersion(String token) {
		try {
			Integer tv = Jwts.parser().verifyWith(key).build()
					.parseSignedClaims(token).getPayload().get(TOKEN_VERSION_CLAIM, Integer.class);
			return tv == null ? 0 : tv;
		} catch (JwtException | IllegalArgumentException e) {
			return 0;
		}
	}
}
