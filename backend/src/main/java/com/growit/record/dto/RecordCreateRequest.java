package com.growit.record.dto;

import com.growit.record.Feeling;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecordCreateRequest(
		@NotNull(message = "활동 분야를 선택해 주세요") Long hobbyId,
		@NotNull(message = "날짜를 입력해 주세요") LocalDate recordDate,
		@NotBlank(message = "한 일을 입력해 주세요") String content,
		@Min(value = 0, message = "연습 시간은 0 이상이어야 해요") Integer minutes,
		String difficulty,
		Feeling feeling,
		Boolean isMilestone) {
}
