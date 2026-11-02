package com.growit.stats.dto;

import java.time.LocalDate;

public record ActivityMapEntry(LocalDate date, Long hobbyId, long count, long minutes) {
}
