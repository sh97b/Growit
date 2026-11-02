package com.growit.record.dto;

import java.time.LocalDate;

/**
 * 기록 상세에서 "이전 기록"·"다음 기록" 링크에 쓰이는 최소 정보.
 *
 * @param id         이웃 기록의 번호
 * @param recordDate 이웃 기록의 날짜
 */
public record RecordNeighbor(Long id, LocalDate recordDate) {
}
