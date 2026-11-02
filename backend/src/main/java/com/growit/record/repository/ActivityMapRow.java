package com.growit.record.repository;

import java.time.LocalDate;

/**
 * {@link ActivityRecordRepository#findActivityMap}의 집계 결과 한 줄(날짜·분야별).
 * 별칭 기반 프로젝션이라 getter 이름이 쿼리의 별칭과 같아야 한다.
 */
public interface ActivityMapRow {

	LocalDate getRecordDate();

	Long getHobbyId();

	/** 그 날짜·분야에 쓰인 기록 수. */
	Long getRecordCount();

	/** 그 날짜·분야의 연습 시간 합(분). null 없이 항상 0 이상. */
	Long getTotalMinutes();
}
