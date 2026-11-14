package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AiCompareResponse(
		boolean locked,
		String lockReason,
		Integer requiredCount,
		Integer currentCount,
		LocalDate oldestRecordDate,
		LocalDate latestRecordDate,
		Long id,
		LocalDate pastDate,
		LocalDate currentDate,
		LocalDateTime createdAt,
		boolean reused,
		LocalDate baseDate,
		boolean anchoredToLatest,
		boolean pastIsOldest,
		String interpretation,
		CompareRecordCard pastRecord,
		CompareRecordCard currentRecord,
		CompareMetrics metrics,
		String feedback) {

	public static AiCompareResponse lockedNotEnoughRecords(int requiredCount, int currentCount) {
		return new AiCompareResponse(true, "NOT_ENOUGH_RECORDS", requiredCount, currentCount, null, null, null, null,
				null, null, false, null, false, false, null, null, null, null, null);
	}

	public static AiCompareResponse lockedNoPastRecord(int requiredCount, int currentCount, LocalDate oldestRecordDate,
			LocalDate latestRecordDate) {
		return new AiCompareResponse(true, "NO_PAST_RECORD", requiredCount, currentCount, oldestRecordDate,
				latestRecordDate, null, null, null, null, false, null, false, false, null, null, null, null, null);
	}
}
