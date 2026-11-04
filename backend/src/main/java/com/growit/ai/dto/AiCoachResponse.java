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
		String reaction,
		/** ACTIVE·CELEBRATE·RESTING·EXPIRED. 홈에서 카드를 어떻게 보여줄지 서버가 판단해 내려준다. */
		String cardState) {

	public static AiCoachResponse locked(int requiredCount, int currentCount) {
		return new AiCoachResponse(true, requiredCount, currentCount, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null, null);
	}
}
