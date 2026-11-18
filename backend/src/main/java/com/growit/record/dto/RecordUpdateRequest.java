package com.growit.record.dto;

import com.growit.record.entity.Feeling;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record RecordUpdateRequest(
		Long hobbyId,
		LocalDate recordDate,
		JsonNullable<String> content,
		JsonNullable<Integer> minutes,
		JsonNullable<String> difficulty,
		JsonNullable<Feeling> feeling,
		Boolean isMilestone,
		/** 직접 추가한 감정의 id. 안 보내면 유지, null이면 비움, 값이 있으면 그 감정으로 바꾼다. */
		JsonNullable<Long> customFeelingId) {

	/** 기본 감정만 다루는 요청. */
	public RecordUpdateRequest(Long hobbyId, LocalDate recordDate, JsonNullable<String> content,
			JsonNullable<Integer> minutes, JsonNullable<String> difficulty, JsonNullable<Feeling> feeling,
			Boolean isMilestone) {
		this(hobbyId, recordDate, content, minutes, difficulty, feeling, isMilestone, JsonNullable.undefined());
	}
}
