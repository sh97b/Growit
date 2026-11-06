package com.growit.ai.dto;

import java.time.LocalDate;

public record WeeklyRequest(LocalDate weekStart, Long hobbyId) {
}
