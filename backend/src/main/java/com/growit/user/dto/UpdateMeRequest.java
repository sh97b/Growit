package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 이름 수정 요청.
 *
 * @param displayName 새 이름. 앞뒤 공백을 지운 뒤 1~30자여야 하고, 공백만 보내면 거절된다
 */
public record UpdateMeRequest(@Schema(example = "서현") String displayName) {
}
