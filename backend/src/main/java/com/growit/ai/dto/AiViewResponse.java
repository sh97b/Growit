package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiViewResponse(
		boolean locked,
		Integer requiredWeeks,
		Integer currentWeeks,
		boolean empty,
		Long id,
		LocalDate startDate,
		LocalDate endDate,
		LocalDateTime createdAt,
		boolean reused,
		String text,
		String editedText,
		String displayText,
		List<CompareRecordCard> evidence,
		String feedback) {

	public static AiViewResponse locked(int requiredWeeks, int currentWeeks) {
		return new AiViewResponse(true, requiredWeeks, currentWeeks, false, null, null, null, null, false, null,
				null, null, List.of(), null);
	}

	public static AiViewResponse emptyWeek() {
		return new AiViewResponse(false, null, null, true, null, null, null, null, false, null, null, null,
				List.of(), null);
	}
}
