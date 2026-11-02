/**
 * 가입·로그인과 보안 설정을 담당한다.
 * <p>{@code controller}·{@code service}·{@code dto}는 가입·로그인 API를 처리하고,
 * {@code security}는 JWT 발급·검증과 {@link com.growit.auth.security.CurrentUserProvider}처럼
 * 다른 도메인이 함께 쓰는 인증 부품을 담는다. {@code config}는 Spring Security 필터 체인을 구성한다.
 * 로그인한 사용자 본인의 정보 조회·수정은 이 패키지가 아니라 {@code com.growit.user}가 맡는다.
 */
package com.growit.auth;
