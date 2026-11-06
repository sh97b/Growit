package com.growit.stats.dto;

import com.growit.record.entity.Feeling;

public record WeekDayRecord(Long id, Long hobbyId, String hobbyName, String content, Integer minutes,
		Feeling feeling) {
}
