package com.growit.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간에 기대는 로직(로그인 시도 제한 등)이 직접 {@code Instant.now()}를 쓰지 않고 이 빈을 받아, 테스트에서 시간을 움직일 수 있게 한다. */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
