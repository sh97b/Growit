package com.growit.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 로그인 기능이 만들어지기 전까지, app.temp-user.enabled=true일 때만 모든 요청을 user id=1로 처리한다.
 * JWT 로그인이 만들어지면 이 클래스는 지운다.
 */
@Component
@ConditionalOnProperty(prefix = "app.temp-user", name = "enabled", havingValue = "true")
public class TempUserCurrentUserProvider implements CurrentUserProvider {

	public static final Long TEMP_USER_ID = 1L;

	@Override
	public Long getCurrentUserId() {
		return TEMP_USER_ID;
	}
}
