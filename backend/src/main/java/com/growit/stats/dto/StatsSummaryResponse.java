package com.growit.stats.dto;

import java.time.LocalDate;

public record StatsSummaryResponse(
		long recordedDays,
		long totalMinutes,
		long hobbyCount,
		LocalDate lastRecordedDate,
		Long daysSinceLastRecord,
		boolean todayRecorded,
		LocalDate today) {
}
