package com.growit.auth.oauth.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * OAuth 콜백에서 토큰을 주소(fragment라도)에 바로 담지 않으려고, 60초짜리 1회용 코드를 메모리에
 * 잠깐 둔다. 여러 서버 인스턴스를 쓰게 되면 이 저장소를 공유 저장소(Redis 등)로 옮겨야 한다(단일 서버 전제).
 */
@Service
public class OauthLoginCodeStore {

	private static final Duration CODE_TTL = Duration.ofSeconds(60);
	private static final int MAX_ENTRIES = 1000;

	public record Entry(Long userId, boolean isNewUser, Instant expiresAt) {
	}

	private final Map<String, Entry> codes = new ConcurrentHashMap<>();
	private final SecureRandom random = new SecureRandom();
	private final Clock clock;

	@Autowired
	public OauthLoginCodeStore() {
		this(Clock.systemUTC());
	}

	OauthLoginCodeStore(Clock clock) {
		this.clock = clock;
	}

	public String issue(Long userId, boolean isNewUser) {
		sweepExpired();
		if (codes.size() >= MAX_ENTRIES) {
			// 비정상적으로 많이 쌓이면(공격 등) 가장 오래된 것부터 지우는 대신, 꽉 찬 상태를 그대로
			// 유지해 메모리 사용량에 상한을 둔다. 정상 트래픽에서는 60초 TTL로 금방 비워진다.
			throw new IllegalStateException("로그인 코드 저장소가 가득 찼어요");
		}
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		codes.put(code, new Entry(userId, isNewUser, clock.instant().plus(CODE_TTL)));
		return code;
	}

	/** 한 번만 쓸 수 있다(바로 제거). 없거나 만료됐으면 빈 값. */
	public Entry consume(String code) {
		Entry entry = codes.remove(code);
		if (entry == null) return null;
		if (entry.expiresAt().isBefore(clock.instant())) return null;
		return entry;
	}

	private void sweepExpired() {
		Instant now = clock.instant();
		codes.values().removeIf(entry -> entry.expiresAt().isBefore(now));
	}
}
