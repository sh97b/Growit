package com.growit.ai.entity;

public enum AiResultType {
	ANALYSIS,
	WEEKLY,
	METRICS,
	COMPARE,
	COACH,
	SIMPLIFY,
	/** 분야 로드맵 생성. 사용자·분야별 하루 한도는 로드맵 서비스가, 사용자별 총량은 AiUsageService가 맡는다. */
	ROADMAP,
	/** 저장되는 ai_results.result_type은 그대로 COACH다. 한도·로그 구분을 위한 호출 종류일 뿐이다. */
	AUTO_COACH
}
