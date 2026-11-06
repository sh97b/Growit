package com.growit.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

	private static final String EMAIL = "someone@example.com";
	private static final String IP = "203.0.113.1";

	/** 테스트에서만 시간을 직접 움직이는 가짜 시계. */
	private static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(java.time.Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	@Test
	void checkAllowed_underThreshold_doesNotThrow() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES - 1; i++) {
			limiter.recordFailure(EMAIL, IP);
		}

		assertThatCode(() -> limiter.checkAllowed(EMAIL, IP)).doesNotThrowAnyException();
	}

	@Test
	void checkAllowed_fiveEmailFailures_locksAndThrowsWithRetryAfter() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES; i++) {
			limiter.recordFailure(EMAIL, "198.51.100." + i);
		}

		assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, "198.51.100.99"))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ErrorCode.LOGIN_LOCKED);
					assertThat(e.getRetryAfterSeconds()).isGreaterThan(0);
				});
	}

	@Test
	void checkAllowed_nonExistentEmailStillLocksAfterFiveFailures() {
		// 이메일이 실제로 있는지는 이 컴포넌트가 몰라도 되고, 문자열 키로 똑같이 센다.
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
		String neverRegisteredEmail = "nobody@example.com";

		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES; i++) {
			limiter.recordFailure(neverRegisteredEmail, "198.51.100." + i);
		}

		assertThatThrownBy(() -> limiter.checkAllowed(neverRegisteredEmail, "198.51.100.200"))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void checkAllowed_thirtyIpFailuresAcrossDifferentEmails_locksIp() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());

		for (int i = 0; i < LoginAttemptLimiter.IP_MAX_FAILURES; i++) {
			limiter.recordFailure("user" + i + "@example.com", IP);
		}

		assertThatThrownBy(() -> limiter.checkAllowed("yet-another@example.com", IP))
				.isInstanceOfSatisfying(ApiException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorCode.LOGIN_LOCKED));
	}

	@Test
	void recordSuccess_clearsOnlyEmailFailureCount() {
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES - 1; i++) {
			limiter.recordFailure(EMAIL, "198.51.100." + i);
		}

		limiter.recordSuccess(EMAIL);
		limiter.recordFailure(EMAIL, "198.51.100.50");

		assertThatCode(() -> limiter.checkAllowed(EMAIL, "198.51.100.51")).doesNotThrowAnyException();
	}

	@Test
	void checkAllowed_afterLockDurationPasses_isAllowedAgain() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES; i++) {
			limiter.recordFailure(EMAIL, "198.51.100." + i);
		}
		assertThatThrownBy(() -> limiter.checkAllowed(EMAIL, "198.51.100.99")).isInstanceOf(ApiException.class);

		clock.advance(LoginAttemptLimiter.EMAIL_LOCK_DURATION.plusSeconds(1));

		assertThatCode(() -> limiter.checkAllowed(EMAIL, "198.51.100.99")).doesNotThrowAnyException();
	}

	@Test
	void checkAllowed_failuresOutsideWindow_doNotAccumulate() {
		MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);
		for (int i = 0; i < LoginAttemptLimiter.EMAIL_MAX_FAILURES - 1; i++) {
			limiter.recordFailure(EMAIL, "198.51.100." + i);
		}

		clock.advance(LoginAttemptLimiter.EMAIL_WINDOW.plusMinutes(1));
		limiter.recordFailure(EMAIL, "198.51.100.50");

		assertThatCode(() -> limiter.checkAllowed(EMAIL, "198.51.100.51")).doesNotThrowAnyException();
	}
}
