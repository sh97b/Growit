package com.growit.dot.dto;

/** goal은 진행 중인 그림(없으면 null), justCompleted는 완성했지만 연출을 아직 안 본 그림(없으면 null). */
public record DotCurrentResponse(DotGoalResponse goal, DotGoalResponse justCompleted) {
}
