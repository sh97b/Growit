package com.growit.auth.security;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider}의 실제 구현. {@link JwtAuthenticationFilter}가
 * {@code SecurityContext}에 심어 둔 사용자 id(Long)를 꺼낸다.
 */
@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

	/**
	 * @throws ApiException {@link ErrorCode#UNAUTHORIZED} 인증 정보가 없을 때(토큰 없음·무효)
	 */
	@Override
	public Long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
		}
		return userId;
	}
}
