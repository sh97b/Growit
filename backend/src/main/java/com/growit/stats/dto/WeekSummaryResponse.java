package com.growit.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record WeekSummaryResponse(
		LocalDate weekStart,
		int recordedDays,
		long recordCount,
		long totalMinutes,
		List<WeekHobbySummary> hobbies,
		WeekFeelingCounts feelings) {
}
