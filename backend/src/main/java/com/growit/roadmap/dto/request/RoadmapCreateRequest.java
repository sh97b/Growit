package com.growit.roadmap.dto.request;

import java.time.LocalDate;

/** 목표일은 선택이다. 날짜만 받고, 남은 일수 같은 값은 만들지 않는다. */
public record RoadmapCreateRequest(LocalDate targetDate) {
}
