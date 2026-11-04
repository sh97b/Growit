package com.growit.ai.dto;

import java.time.LocalDate;

public record CoachEvidence(Long recordId, LocalDate recordDate, String content) {
}
