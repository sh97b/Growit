package com.growit.user.dto;

import java.time.LocalDateTime;

/**
 * 내 정보 조회·수정 응답.
 *
 * @param id          사용자 번호
 * @param email       로그인 이메일(소문자)
 * @param displayName 화면에 보일 이름
 * @param createdAt   가입 시각
 */
public record MeResponse(Long id, String email, String displayName, LocalDateTime createdAt) {
}
