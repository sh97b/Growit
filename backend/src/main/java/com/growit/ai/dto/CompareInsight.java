package com.growit.ai.dto;

import java.util.List;

/** 좋아진 점·더 키워 볼 점 한 항목(point: 한 줄 요약, because: 이유). */
public record CompareInsight(String point, String because, List<CompareEvidence> evidence) {
}
