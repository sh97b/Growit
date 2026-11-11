package com.growit.auth.oauth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OauthExchangeRateLimiterTest {

	@Test
	void checkAllowed_within20CallsPerMinute_doesNotThrow() {
		OauthExchangeRateLimiter limiter = new OauthExchangeRateLimiter(Clock.fixed(Instant.now(), ZoneOffset.UTC));

		for (int i = 0; i < 20; i++) {
			assertThatCode(() -> limiter.checkAllowed("203.0.113.1")).doesNotThrowAnyException();
		}
	}

	@Test
	void checkAllowed_21stCallWithinWindow_throwsLoginLocked() {
		OauthExchangeRateLimiter limiter = new OauthExchangeRateLimiter(Clock.fixed(Instant.now(), ZoneOffset.UTC));
		for (int i = 0; i < 20; i++) {
			limiter.checkAllowed("203.0.113.2");
		}

		assertThatThrownBy(() -> limiter.checkAllowed("203.0.113.2"))
				.isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions.assertThat(e.getCode())
						.isEqualTo(ErrorCode.LOGIN_LOCKED));
	}

	@Test
	void checkAllowed_differentIps_countedSeparately() {
		OauthExchangeRateLimiter limiter = new OauthExchangeRateLimiter(Clock.fixed(Instant.now(), ZoneOffset.UTC));
		for (int i = 0; i < 20; i++) {
			limiter.checkAllowed("203.0.113.3");
		}

		assertThatCode(() -> limiter.checkAllowed("203.0.113.4")).doesNotThrowAnyException();
	}
}
