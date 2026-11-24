package com.growit.ai.dto;

import java.util.List;

/** "이렇게 해 보면 좋아요" 한 항목. */
public record CompareIdea(String idea, List<CompareEvidence> evidence) {
}
