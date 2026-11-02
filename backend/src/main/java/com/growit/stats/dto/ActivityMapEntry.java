package com.growit.stats.dto;

import java.time.LocalDate;

/**
 * 활동 지도의 한 칸(특정 날짜·분야의 집계). 기록이 없는 날짜·분야 조합은 아예 포함되지 않는다.
 *
 * @param date    집계 날짜
 * @param hobbyId 집계 분야
 * @param count   그 날짜·분야에 쓰인 기록 수
 * @param minutes 그 날짜·분야의 연습 시간 합(분)
 */
public record ActivityMapEntry(LocalDate date, Long hobbyId, long count, long minutes) {
}
