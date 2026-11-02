package com.growit.hobby.dto;

/**
 * 활동 분야 조회·등록·수정 응답.
 *
 * @param id          분야 번호
 * @param name        분야 이름
 * @param goal        목표 한 줄, 없으면 null
 * @param recordCount 이 분야에 쓰인 기록 수
 * @param archived    보관 여부(true면 새 기록을 쓸 수 없음)
 */
public record HobbyResponse(Long id, String name, String goal, long recordCount, boolean archived) {
}
