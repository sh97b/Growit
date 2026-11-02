package com.growit.record.dto;

import com.growit.record.entity.Feeling;
import java.time.LocalDate;

/**
 * 기록 목록·상세 응답.
 *
 * @param id             기록 번호
 * @param hobbyId        분야 번호
 * @param recordDate     활동한 날짜
 * @param content        한 일
 * @param minutes        연습 시간(분), 없으면 null
 * @param difficulty     막힌 점이나 나아진 점, 없으면 null
 * @param feeling        오늘의 느낌, 없으면 null
 * @param isMilestone    의미 있는 순간 여부
 * @param analysis       AI가 뽑은 주제·어려움, 아직 없거나 실패했으면 null
 * @param previousRecord 같은 분야의 바로 이전 기록, 없으면 null. 상세 조회에서만 채워지고 목록에서는 항상 null
 * @param nextRecord     같은 분야의 바로 다음 기록, 없으면 null. 상세 조회에서만 채워지고 목록에서는 항상 null
 */
public record RecordResponse(
		Long id,
		Long hobbyId,
		LocalDate recordDate,
		String content,
		Integer minutes,
		String difficulty,
		Feeling feeling,
		boolean isMilestone,
		Object analysis,
		RecordNeighbor previousRecord,
		RecordNeighbor nextRecord) {
}
