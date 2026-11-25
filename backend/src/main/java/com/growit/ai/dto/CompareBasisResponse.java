package com.growit.ai.dto;

import java.time.LocalDate;

/**
 * 변화 탭 기본 데이터(AI 없이 즉시). 잠겼으면 locked=true와 사유만 있고, 아니면 이전·최근 기록과 숫자가 채워진다.
 * baseDate·anchoredToLatest·pastIsOldest·pastDate는 "마지막 기록(날짜)을 기준으로 비교했어요" 같은 안내 문장에 쓴다.
 */
public record CompareBasisResponse(
		boolean locked,
		String lockReason,
		Integer requiredCount,
		Integer currentCount,
		LocalDate oldestRecordDate,
		LocalDate latestRecordDate,
		LocalDate baseDate,
		boolean anchoredToLatest,
		boolean pastIsOldest,
		LocalDate pastDate,
		LocalDate currentDate,
		CompareRecordCard previous,
		CompareRecordCard latest,
		CompareMetrics numbers) {

	public static CompareBasisResponse locked(String lockReason, Integer requiredCount, Integer currentCount,
			LocalDate oldestRecordDate, LocalDate latestRecordDate) {
		return new CompareBasisResponse(true, lockReason, requiredCount, currentCount, oldestRecordDate,
				latestRecordDate, null, false, false, null, null, null, null, null);
	}
}
