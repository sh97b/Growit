package com.growit.ai.dto;

import java.time.LocalDate;

/** 근거 기록 한 건. 날짜와 내용 일부는 AI가 아니라 서버가 저장된 기록에서 채운다. */
public record CompareEvidence(Long id, LocalDate recordDate, String content) {
}
