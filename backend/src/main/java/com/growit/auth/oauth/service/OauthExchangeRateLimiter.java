package com.growit.auth.oauth.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** exchange는 로그인 코드를 추측해 보는 공격의 표적이 될 수 있어 IP당 분당 호출 수를 제한한다. */
@Component
public class OauthExchangeRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);
	private static final int MAX_CALLS_PER_WINDOW = 20;
	private static final int MAX_TRACKED_KEYS = 20_000;

	private final Clock clock;
	private final ConcurrentHashMap<String, Deque<Instant>> callsByIp = new ConcurrentHashMap<>();

	public OauthExchangeRateLimiter() {
		this(Clock.systemUTC());
	}

	OauthExchangeRateLimiter(Clock clock) {
		this.clock = clock;
	}

	public void checkAllowed(String ip) {
		Instant now = clock.instant();
		if (callsByIp.size() >= MAX_TRACKED_KEYS) {
			callsByIp.entrySet().removeIf(entry -> isStale(entry.getValue(), now));
		}
		Deque<Instant> calls = callsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
		synchronized (calls) {
			Instant windowStart = now.minus(WINDOW);
			while (!calls.isEmpty() && calls.peekFirst().isBefore(windowStart)) {
				calls.removeFirst();
			}
			if (calls.size() >= MAX_CALLS_PER_WINDOW) {
				throw new ApiException(ErrorCode.LOGIN_LOCKED, "잠시 후 다시 시도해 주세요", WINDOW.toSeconds());
			}
			calls.addLast(now);
		}
	}

	private boolean isStale(Deque<Instant> calls, Instant now) {
		synchronized (calls) {
			return calls.isEmpty() || calls.peekLast().isBefore(now.minus(WINDOW));
		}
	}
}
