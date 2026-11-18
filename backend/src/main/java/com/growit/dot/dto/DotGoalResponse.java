package com.growit.dot.dto;

import com.growit.dot.entity.DotGoalState;
import com.growit.dot.entity.DotSourceType;
import java.time.LocalDate;
import java.util.List;

public record DotGoalResponse(
		Long id,
		String title,
		DotSourceType sourceType,
		String presetKey,
		int goalDays,
		int filledCount,
		int width,
		int height,
		List<String> palette,
		List<DotCell> cells,
		LocalDate startedOn,
		DotGoalState state,
		LocalDate completedOn,
		boolean completionSeen) {
}
