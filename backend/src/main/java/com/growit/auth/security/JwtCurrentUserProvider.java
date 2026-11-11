package com.growit.auth.security;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

	@Override
	public Long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			throw new ApiException(ErrorCode.TOKEN_INVALID, "로그인이 필요해요");
		}
		return userId;
	}
}
