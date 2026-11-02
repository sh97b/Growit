package com.growit.record.dto;

import com.growit.record.entity.Feeling;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 기록 등록 요청.
 *
 * @param hobbyId     기록을 남길 분야. 내 활성 분야여야 한다(보관된 분야면 거절)
 * @param recordDate  활동한 날짜. 서버 시간(Asia/Seoul) 기준 오늘보다 미래면 거절
 * @param content     한 일. 필수, 빈 값이면 거절
 * @param minutes     연습 시간(분), 0 이상. 선택 입력
 * @param difficulty  막힌 점이나 나아진 점. 선택 입력
 * @param feeling     오늘의 느낌. 선택 입력
 * @param isMilestone 의미 있는 순간 표시. 생략하면 false
 */
public record RecordCreateRequest(
		@Schema(example = "1") @NotNull(message = "활동 분야를 선택해 주세요") Long hobbyId,
		@Schema(example = "2026-10-28") @NotNull(message = "날짜를 입력해 주세요") LocalDate recordDate,
		@Schema(example = "메트로놈 60에 맞춰 코드 전환 연습") @NotBlank(message = "한 일을 입력해 주세요") String content,
		@Schema(example = "20") @Min(value = 0, message = "연습 시간은 0 이상이어야 해요") Integer minutes,
		@Schema(example = "코드 전환이 느렸다") String difficulty,
		@Schema(example = "PROUD", description = "PROUD(뿌듯함), STUCK(답답함), RELIEVED(개운함), NEUTRAL(잘 모르겠음)") Feeling feeling,
		@Schema(example = "false") Boolean isMilestone) {
}
