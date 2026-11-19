package com.growit.ai.dto;

import java.util.List;

public record CoachDirectionResponse(String direction, List<CoachEvidence> evidence, List<String> steps) {
}
