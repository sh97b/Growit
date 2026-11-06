package com.growit.ai.dto;

import java.time.LocalDate;

public record MetricsRequest(Long hobbyId, LocalDate from, LocalDate to) {
}
