package com.growit.auth.dto;

/**
 * 회원가입 성공 응답.
 *
 * @param id    새로 만들어진 사용자 번호
 * @param email 소문자로 통일되어 저장된 이메일
 */
public record SignupResponse(Long id, String email) {
}
