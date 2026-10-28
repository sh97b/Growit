package com.growit.record.dto;

import com.growit.record.Feeling;
import java.time.LocalDate;

public record RecordResponse(
		Long id,
		Long hobbyId,
		LocalDate recordDate,
		String content,
		Integer minutes,
		String difficulty,
		Feeling feeling,
		boolean isMilestone,
		Object analysis) {
}
