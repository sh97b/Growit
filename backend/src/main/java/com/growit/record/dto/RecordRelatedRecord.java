package com.growit.record.dto;

import java.time.LocalDate;

public record RecordRelatedRecord(Long id, LocalDate recordDate, String content) {
}
