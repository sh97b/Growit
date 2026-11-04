package com.growit.ai.dto;

import java.time.LocalDate;
import java.util.List;

public record CompareRecordCard(Long id, LocalDate recordDate, String content, List<String> tags, String feeling) {
}
