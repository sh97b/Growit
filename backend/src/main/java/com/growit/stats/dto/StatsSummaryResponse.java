package com.growit.stats.dto;

import java.time.LocalDate;

/**
 * 홈 화면 상태 판단에 쓰이는 활동 요약.
 *
 * @param recordedDays        기록을 남긴 서로 다른 날의 수(같은 날 여러 건은 하루로 센다)
 * @param totalMinutes        누적 연습 시간(분)
 * @param hobbyCount          보관되지 않은(활성) 분야 수
 * @param lastRecordedDate    마지막으로 기록한 날짜. 기록이 하나도 없으면 null
 * @param daysSinceLastRecord 마지막 기록 이후 지난 일수({@code today - lastRecordedDate}). 기록이 없으면 null
 * @param todayRecorded       오늘(서버 시간 Asia/Seoul 기준) 기록을 남겼는지
 * @param today               이 응답을 만든 기준 날짜(서버 시간 Asia/Seoul)
 */
public record StatsSummaryResponse(
		long recordedDays,
		long totalMinutes,
		long hobbyCount,
		LocalDate lastRecordedDate,
		Long daysSinceLastRecord,
		boolean todayRecorded,
		LocalDate today) {
}
