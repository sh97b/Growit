package com.growit.auth;

import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class JwtCurrentUserProvider implements CurrentUserProvider {

	@Override
	public Long getCurrentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
			throw new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요");
		}
		return userId;
	}
}
