package com.growit.record.dto;

import com.growit.record.Feeling;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record RecordCreateRequest(
		@Schema(example = "1") @NotNull(message = "활동 분야를 선택해 주세요") Long hobbyId,
		@Schema(example = "2026-10-28") @NotNull(message = "날짜를 입력해 주세요") LocalDate recordDate,
		@Schema(example = "메트로놈 60에 맞춰 코드 전환 연습") @NotBlank(message = "한 일을 입력해 주세요") String content,
		@Schema(example = "20") @Min(value = 0, message = "연습 시간은 0 이상이어야 해요") Integer minutes,
		@Schema(example = "코드 전환이 느렸다") String difficulty,
		@Schema(example = "PROUD", description = "PROUD(뿌듯함), REGRET(아쉬움), STUCK(막막함), NEUTRAL(평범함)") Feeling feeling,
		@Schema(example = "false") Boolean isMilestone) {
}
