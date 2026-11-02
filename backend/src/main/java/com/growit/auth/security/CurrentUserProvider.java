package com.growit.auth.security;

/**
 * 현재 로그인한 사용자의 id를 꺼내는 유일한 통로.
 * <p>컨트롤러는 항상 이 인터페이스를 통해서만 사용자 id를 얻고, 요청 본문이나 주소에서 직접 받지 않는다.
 * 실제 구현은 {@link JwtCurrentUserProvider}.
 */
public interface CurrentUserProvider {

	Long getCurrentUserId();
}
