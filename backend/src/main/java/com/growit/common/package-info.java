/**
 * 어느 도메인에도 속하지 않는 공통 부품을 모은다.
 * <p>오류 응답 모양과 처리({@code exception}), 환경 설정({@code config}: CORS·Jackson·Swagger),
 * 배포 확인용 엔드포인트({@code controller})가 여기에 속한다. 로그인·사용자 식별처럼
 * 보안과 맞닿은 공통 부품은 이 패키지가 아니라 {@code com.growit.auth}에 둔다.
 */
package com.growit.common;
