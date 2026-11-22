package com.growit.roadmap.dto.request;

import java.time.LocalDate;

/**
 * 목표일은 선택이다. 날짜만 받고, 남은 일수 같은 값은 만들지 않는다.
 * mode가 "MANUAL"이면 AI 없이 빈 로드맵을 만들고, 없거나 그 밖의 값이면 AI로 만든다.
 */
public record RoadmapCreateRequest(LocalDate targetDate, String mode) {

	public boolean isManual() {
		return "MANUAL".equalsIgnoreCase(mode);
	}
}
