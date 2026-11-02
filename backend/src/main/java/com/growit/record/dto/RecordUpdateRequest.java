package com.growit.record.dto;

import com.growit.record.entity.Feeling;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * 기록 수정 요청. 칸을 보내지 않으면 그대로 두고, 보낸 칸만 바꾼다.
 *
 * @param hobbyId     바꿀 분야. 보내지 않으면 유지, 보내면 내 활성 분야여야 한다
 * @param recordDate  바꿀 날짜. 보내지 않으면 유지, 보내면 서버 기준 오늘보다 미래일 수 없다
 * @param content     바꿀 한 일. {@link JsonNullable#isPresent()}가 false면 유지. 비울 수 없어서
 *                    null이거나 빈 값으로 보내면 거절된다
 * @param minutes     바꿀 연습 시간. 보내지 않으면 유지, {@code null}을 보내면 비운다
 * @param difficulty  바꿀 막힌 점. 보내지 않으면 유지, {@code null}을 보내면 비운다
 * @param feeling     바꿀 느낌. 보내지 않으면 유지, {@code null}을 보내면 비운다
 * @param isMilestone 바꿀 의미 있는 순간 표시. 보내지 않으면 유지
 */
public record RecordUpdateRequest(
		Long hobbyId,
		LocalDate recordDate,
		JsonNullable<String> content,
		JsonNullable<Integer> minutes,
		JsonNullable<String> difficulty,
		JsonNullable<Feeling> feeling,
		Boolean isMilestone) {
}
