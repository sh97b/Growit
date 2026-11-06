package com.growit.auth.security;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 메모리에만 저장하는 로그인 시도 제한이다(새 라이브러리 없이 맵으로 직접 구현). 서버를 다시 시작하면
 * 모든 기록이 초기화되는 한계가 있고, 여러 인스턴스로 늘리면 인스턴스마다 따로 센다 - 지금은 단일
 * 인스턴스 배포라 받아들인 한계다. 맵이 끝없이 커지는 것을 막으려고 가득 차면 잠겨 있지 않은 오래된
 * 항목부터 정리한다.
 */
@Component
public class LoginAttemptLimiter {

	static final Duration EMAIL_WINDOW = Duration.ofMinutes(10);
	static final int EMAIL_MAX_FAILURES = 5;
	static final Duration EMAIL_LOCK_DURATION = Duration.ofMinutes(5);

	static final Duration IP_WINDOW = Duration.ofMinutes(10);
	static final int IP_MAX_FAILURES = 30;
	static final Duration IP_LOCK_DURATION = Duration.ofMinutes(5);

	private static final int MAX_TRACKED_KEYS = 20_000;
	private static final String LOCKED_MESSAGE = "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요";

	private final Clock clock;
	private final ConcurrentHashMap<String, Bucket> emailBuckets = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

	public LoginAttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	/** 이메일이 실제로 있는지와 무관하게 똑같이 막는다 - 계정 존재 여부가 드러나면 안 되기 때문이다. */
	public void checkAllowed(String email, String ip) {
		Instant now = clock.instant();
		long remaining = Math.max(remainingLockSeconds(emailBuckets, email, now),
				remainingLockSeconds(ipBuckets, ip, now));
		if (remaining > 0) {
			throw new ApiException(ErrorCode.LOGIN_LOCKED, LOCKED_MESSAGE, remaining);
		}
	}

	public void recordFailure(String email, String ip) {
		Instant now = clock.instant();
		recordFailure(emailBuckets, email, now, EMAIL_WINDOW, EMAIL_MAX_FAILURES, EMAIL_LOCK_DURATION);
		recordFailure(ipBuckets, ip, now, IP_WINDOW, IP_MAX_FAILURES, IP_LOCK_DURATION);
	}

	/** IP 쪽 실패 집계는 그대로 두고, 성공한 이메일의 실패 횟수만 지운다. */
	public void recordSuccess(String email) {
		Bucket bucket = emailBuckets.get(email);
		if (bucket != null) {
			bucket.reset();
		}
	}

	private long remainingLockSeconds(Map<String, Bucket> buckets, String key, Instant now) {
		Bucket bucket = buckets.get(key);
		if (bucket == null) {
			return 0;
		}
		return bucket.remainingLock(now).map(d -> Math.max(1, d.toSeconds() + 1)).orElse(0L);
	}

	private void recordFailure(ConcurrentHashMap<String, Bucket> buckets, String key, Instant now, Duration window,
			int maxFailures, Duration lockDuration) {
		evictStaleIfFull(buckets, now, window);
		buckets.computeIfAbsent(key, k -> new Bucket()).recordFailure(now, window, maxFailures, lockDuration);
	}

	private void evictStaleIfFull(ConcurrentHashMap<String, Bucket> buckets, Instant now, Duration window) {
		if (buckets.size() < MAX_TRACKED_KEYS) {
			return;
		}
		buckets.entrySet().removeIf(entry -> entry.getValue().isStale(now, window));
	}

	private static final class Bucket {
		private final Deque<Instant> failures = new ArrayDeque<>();
		private Instant lockedUntil;

		synchronized Optional<Duration> remainingLock(Instant now) {
			if (lockedUntil != null && lockedUntil.isAfter(now)) {
				return Optional.of(Duration.between(now, lockedUntil));
			}
			return Optional.empty();
		}

		synchronized void recordFailure(Instant now, Duration window, int maxFailures, Duration lockDuration) {
			failures.addLast(now);
			Instant windowStart = now.minus(window);
			while (!failures.isEmpty() && failures.peekFirst().isBefore(windowStart)) {
				failures.removeFirst();
			}
			if (failures.size() >= maxFailures) {
				lockedUntil = now.plus(lockDuration);
				failures.clear();
			}
		}

		synchronized void reset() {
			failures.clear();
			lockedUntil = null;
		}

		synchronized boolean isStale(Instant now, Duration window) {
			if (lockedUntil != null && lockedUntil.isAfter(now)) {
				return false;
			}
			return failures.isEmpty() || failures.peekLast().isBefore(now.minus(window));
		}
	}
}
