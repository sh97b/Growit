package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiCoachResponse(
		boolean locked,
		Integer requiredCount,
		Integer currentCount,
		Long id,
		String mode,
		LocalDate startDate,
		LocalDate endDate,
		LocalDateTime createdAt,
		boolean reused,
		String periodSummary,
		List<CoachTextItemResponse> changes,
		List<CoachTextItemResponse> repeatedDifficulties,
		CoachActionResponse nextAction,
		CoachActionResponse comebackAction,
		Integer recordsRead,
		String feedback,
		String reaction) {

	public static AiCoachResponse locked(int requiredCount, int currentCount) {
		return new AiCoachResponse(true, requiredCount, currentCount, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null);
	}
}
