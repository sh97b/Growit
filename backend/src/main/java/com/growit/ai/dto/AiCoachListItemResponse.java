package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AiCoachListItemResponse(
		Long id,
		LocalDateTime createdAt,
		LocalDate startDate,
		LocalDate endDate,
		Long hobbyId,
		String hobbyName,
		String summary,
		String nextActionTitle,
		boolean isAuto,
		boolean isNew,
		String feedback,
		String reaction) {
}
