package com.growit.ai.dto;

import java.util.List;

public record CoachTextItemResponse(String text, List<CoachEvidence> evidence) {
}
