package com.growit.record.repository;

import java.time.LocalDate;

public interface ActivityMapRow {

	LocalDate getRecordDate();

	Long getHobbyId();

	Long getRecordCount();

	Long getTotalMinutes();
}
