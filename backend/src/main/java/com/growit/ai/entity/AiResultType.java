package com.growit.ai.entity;

public enum AiResultType {
	ANALYSIS,
	WEEKLY,
	METRICS,
	COMPARE,
	COACH,
	SIMPLIFY,
	/** 저장되는 ai_results.result_type은 그대로 COACH다. 한도·로그 구분을 위한 호출 종류일 뿐이다. */
	AUTO_COACH
}
