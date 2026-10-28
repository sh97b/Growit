package com.growit.auth;

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
 * 사용자 id만 담는 HS256 JWT를 만들고 읽는다.
 * JWT_SECRET이 없거나 32바이트(HS256 최소 길이)보다 짧으면, 이 빈이 만들어지는 시점(서버 시작 시)에 실패한다.
 */
@Component
public class JwtTokenProvider {

	private final SecretKey key;
	private final Duration expiration;

	public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-days}") long expirationDays) {
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.expiration = Duration.ofDays(expirationDays);
	}

	public String createToken(Long userId) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expiration)))
				.signWith(key)
				.compact();
	}

	/**
	 * 토큰이 없거나 위조·만료됐으면 빈 값을 돌려준다. 예외를 던지지 않는다.
	 */
	public Optional<Long> parseUserId(String token) {
		try {
			String subject = Jwts.parser().verifyWith(key).build()
					.parseSignedClaims(token).getPayload().getSubject();
			return Optional.of(Long.parseLong(subject));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}
}
