package com.growit.record.dto;

import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import java.time.LocalDate;

public record RecordSummaryResponse(
		Long id,
		Long hobbyId,
		LocalDate recordDate,
		String content,
		Integer minutes,
		String difficulty,
		Feeling feeling,
		boolean isMilestone,
		AiStatus aiStatus,
		RecordAnalysisSummary analysis) {
}
