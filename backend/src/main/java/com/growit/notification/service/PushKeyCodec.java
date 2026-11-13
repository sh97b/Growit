package com.growit.notification.service;

import java.util.Base64;

/**
 * 브라우저가 주는 구독 키(p256dh·auth)는 base64url(패딩 없음)이 표준이지만 일부 환경에서 표준 base64가
 * 섞여 들어올 수 있어 둘 다 받아들이게 정규화한다. 형식 검증(길이·접두 바이트)도 여기서 한다.
 */
final class PushKeyCodec {

	/** EC P-256 공개키(비압축)는 0x04 + x(32바이트) + y(32바이트) = 65바이트여야 한다. */
	private static final int P256DH_LENGTH = 65;
	private static final byte P256DH_PREFIX = 0x04;
	private static final int AUTH_LENGTH = 16;

	private PushKeyCodec() {
	}

	/** base64url·base64, 패딩 유무 어느 쪽이든 디코딩한다. 형식이 아예 깨졌으면 {@code null}. */
	static byte[] decode(String raw) {
		if (raw == null) {
			return null;
		}
		String normalized = raw.trim().replace('-', '+').replace('_', '/');
		int remainder = normalized.length() % 4;
		if (remainder != 0) {
			normalized = normalized + "=".repeat(4 - remainder);
		}
		try {
			return Base64.getDecoder().decode(normalized);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static boolean isValidP256dh(byte[] decoded) {
		return decoded != null && decoded.length == P256DH_LENGTH && decoded[0] == P256DH_PREFIX;
	}

	static boolean isValidAuth(byte[] decoded) {
		return decoded != null && decoded.length == AUTH_LENGTH;
	}
}
