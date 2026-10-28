package com.growit.common;

/**
 * 현재 로그인한 사용자의 id를 꺼내는 유일한 통로.
 * 컨트롤러는 항상 이 인터페이스를 통해서만 사용자 id를 얻는다.
 */
public interface CurrentUserProvider {

	Long getCurrentUserId();
}
