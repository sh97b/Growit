package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * remainingText·remainingEvidence는 WEEKLY의 "아직 남은 어려움" 섹션 전용이다(promptVersion 2부터).
 * 그 전 결과나 METRICS 결과는 remainingText가 null이고 remainingEvidence가 빈 목록이라,
 * 프론트는 그 경우 두 번째 섹션을 그리지 않는다.
 */
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
		String feedback,
		String remainingText,
		List<CompareRecordCard> remainingEvidence) {

	public static AiViewResponse locked(int requiredWeeks, int currentWeeks) {
		return new AiViewResponse(true, requiredWeeks, currentWeeks, false, null, null, null, null, false, null,
				null, null, List.of(), null, null, List.of());
	}

	public static AiViewResponse emptyWeek() {
		return new AiViewResponse(false, null, null, true, null, null, null, null, false, null, null, null,
				List.of(), null, null, List.of());
	}
}
