package com.growit.stats.dto;

import java.time.LocalDate;
import java.util.List;

public record WeekDay(LocalDate date, List<WeekDayRecord> records) {
}
