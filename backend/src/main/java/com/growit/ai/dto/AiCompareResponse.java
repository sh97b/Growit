package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AiCompareResponse(
		boolean locked,
		Integer requiredCount,
		Integer currentCount,
		Long id,
		LocalDate pastDate,
		LocalDate currentDate,
		LocalDateTime createdAt,
		boolean reused,
		String interpretation,
		CompareRecordCard pastRecord,
		CompareRecordCard currentRecord,
		CompareMetrics metrics,
		String feedback) {

	public static AiCompareResponse locked(int requiredCount, int currentCount) {
		return new AiCompareResponse(true, requiredCount, currentCount, null, null, null, null, false, null, null,
				null, null, null);
	}
}
