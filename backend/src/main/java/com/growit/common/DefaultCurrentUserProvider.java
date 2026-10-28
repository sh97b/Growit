package com.growit.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 임시 사용자(app.temp-user.enabled)가 꺼져 있을 때 쓰이는 기본 CurrentUserProvider.
 * 아직 로그인 기능이 없으므로 호출되면 항상 401을 내보낸다.
 * 11/7에 JWT 로그인을 붙이면 이 클래스를 로그인 기반 구현체로 바꾼다.
 */
@Component
@ConditionalOnProperty(prefix = "app.temp-user", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DefaultCurrentUserProvider implements CurrentUserProvider {

	@Override
	public Long getCurrentUserId() {
		throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
	}
}
