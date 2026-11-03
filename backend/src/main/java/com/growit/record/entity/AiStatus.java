package com.growit.record.entity;

/** NONE=분석 전/초기화됨, PENDING=분석 대기 중, DONE=완료, FAILED=실패, SKIPPED=AI 꺼짐으로 건너뜀. */
public enum AiStatus {
	NONE,
	PENDING,
	DONE,
	FAILED,
	SKIPPED
}
