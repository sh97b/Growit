package com.growit.record.dto;

import com.growit.record.Feeling;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record RecordUpdateRequest(
		Long hobbyId,
		LocalDate recordDate,
		JsonNullable<String> content,
		JsonNullable<Integer> minutes,
		JsonNullable<String> difficulty,
		JsonNullable<Feeling> feeling,
		Boolean isMilestone) {
}
