package com.growit.hobby.dto;

import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 활동 분야 수정 요청. 칸을 보내지 않으면 그대로 두고, 보낸 칸만 바꾼다.
 *
 * @param name     바꿀 이름. 보내지 않으면 유지, 빈 값이거나 중복되면 거절된다
 * @param goal     바꿀 목표. {@link JsonNullable#isPresent()}가 false면 유지, {@code null}을 보내면 비운다
 * @param archived true면 보관, false면 되살리기. 보내지 않으면 보관 상태를 바꾸지 않는다
 */
public record HobbyUpdateRequest(String name, JsonNullable<String> goal, Boolean archived) {
}
