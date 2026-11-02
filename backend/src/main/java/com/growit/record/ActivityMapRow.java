package com.growit.record;

import java.time.LocalDate;

public interface ActivityMapRow {

	LocalDate getRecordDate();

	Long getHobbyId();

	Long getRecordCount();

	Long getTotalMinutes();
}
