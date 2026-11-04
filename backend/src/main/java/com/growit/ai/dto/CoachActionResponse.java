package com.growit.ai.dto;

import java.util.List;

public record CoachActionResponse(String title, Integer minutes, String reason, List<CoachEvidence> evidence) {
}
