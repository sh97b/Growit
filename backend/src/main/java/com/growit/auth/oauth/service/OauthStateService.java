package com.growit.auth.oauth.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * state 값은 무작위 바이트 + 만료 시각을 JWT_SECRET에서 파생한 키로 서명해, 쿠키와 별개로도
 * 위조·재사용(만료 지남)을 스스로 검증할 수 있게 한다. 쿠키 값과 콜백의 state 파라미터가 똑같은지도
 * 함께 확인해야 CSRF(공격자가 자기 state로 피해자를 콜백 주소로 유도하는 것)를 막을 수 있다.
 */
@Service
public class OauthStateService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String KEY_DERIVATION_LABEL = "oauth-state";
	private static final Duration STATE_TTL = Duration.ofMinutes(5);

	private final SecretKeySpec signingKey;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();

	public OauthStateService(@Value("${app.jwt.secret}") String jwtSecret) {
		this(jwtSecret, Clock.systemUTC());
	}

	OauthStateService(String jwtSecret, Clock clock) {
		this.signingKey = deriveKey(jwtSecret);
		this.clock = clock;
	}

	private static SecretKeySpec deriveKey(String jwtSecret) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] derived = mac.doFinal(KEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(derived, HMAC_ALGORITHM);
		} catch (Exception e) {
			throw new IllegalStateException("OAuth state 서명 키를 만들지 못했어요", e);
		}
	}

	/** 쿠키 값이자 제공자에게 보내는 state 파라미터로 함께 쓸 서명된 토큰을 만든다. */
	public String issue() {
		byte[] nonce = new byte[16];
		random.nextBytes(nonce);
		long expiresAtEpochMillis = clock.instant().plus(STATE_TTL).toEpochMilli();

		byte[] payload = new byte[nonce.length + Long.BYTES];
		System.arraycopy(nonce, 0, payload, 0, nonce.length);
		for (int i = 0; i < Long.BYTES; i++) {
			payload[nonce.length + i] = (byte) (expiresAtEpochMillis >>> (8 * (Long.BYTES - 1 - i)));
		}

		String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
		String signature = sign(encodedPayload);
		return encodedPayload + "." + signature;
	}

	/** 쿠키 값과 콜백 state 파라미터가 같고, 서명·만료 모두 유효해야 통과한다. */
	public boolean verify(String cookieValue, String queryState) {
		if (cookieValue == null || queryState == null || !cookieValue.equals(queryState)) {
			return false;
		}
		int dotIndex = cookieValue.lastIndexOf('.');
		if (dotIndex < 0) return false;
		String encodedPayload = cookieValue.substring(0, dotIndex);
		String signature = cookieValue.substring(dotIndex + 1);
		if (!constantTimeEquals(sign(encodedPayload), signature)) {
			return false;
		}
		try {
			byte[] payload = Base64.getUrlDecoder().decode(encodedPayload);
			if (payload.length != 16 + Long.BYTES) return false;
			long expiresAtEpochMillis = 0;
			for (int i = 0; i < Long.BYTES; i++) {
				expiresAtEpochMillis = (expiresAtEpochMillis << 8) | (payload[16 + i] & 0xFF);
			}
			return clock.instant().toEpochMilli() <= expiresAtEpochMillis;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private String sign(String encodedPayload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(signingKey);
			byte[] signature = mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
		} catch (Exception e) {
			throw new IllegalStateException("OAuth state 서명에 실패했어요", e);
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) return false;
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}
}
