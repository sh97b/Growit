package com.growit.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record WeekDetailResponse(
		LocalDate weekStart,
		LocalDate weekEnd,
		int recordedDays,
		long totalMinutes,
		long hobbyCount,
		List<WeekDay> days,
		LocalDate previousWeekStart,
		LocalDate nextWeekStart) {
}
