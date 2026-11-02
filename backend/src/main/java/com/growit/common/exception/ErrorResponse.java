package com.growit.common.exception;

/**
 * 모든 오류 응답이 공통으로 쓰는 몸체. {@code {"code":"VALIDATION_ERROR","message":"..."}} 모양이다.
 *
 * @param code    {@link ErrorCode}의 이름(예: "NOT_FOUND")
 * @param message 사용자에게 그대로 보여 줄 수 있는 한국어 안내문
 */
public record ErrorResponse(String code, String message) {
}
