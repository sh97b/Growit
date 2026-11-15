package com.growit.ai.dto;

/**
 * POST /api/ai/coach/auto 전용 응답(COACH/COMPARE 등 조회용 {@link AiCoachResponse}와는 모양이 다르다).
 * status로 "왜 지금 만들지 않았는지"를 분명히 구분해 프론트가 진행 막대·안내를 정확히 보여줄 수 있게 한다.
 */
public record AiCoachAutoResponse(
		Status status,
		AiCoachResponse result,
		int newRecordsSinceLast,
		int requiredCount,
		boolean hasAnyCoach) {

	public enum Status {
		/** 새로 만들어 저장함. */
		GENERATED,
		/** 같은 입력으로 이미 만들어진 결과를 그대로 재사용함(동시 호출 등). */
		EXISTS,
		/** 아직 조건(기록 5개)을 못 채움. */
		NOT_ELIGIBLE,
		/** 서버 또는 내 AI 설정이 꺼져 있음. */
		DISABLED,
		/** 하루 자동 회고 한도를 다 씀. */
		LIMIT
	}

	public static AiCoachAutoResponse generated(AiCoachResponse result, int requiredCount, boolean hasAnyCoach) {
		return new AiCoachAutoResponse(Status.GENERATED, result, requiredCount, requiredCount, hasAnyCoach);
	}

	public static AiCoachAutoResponse exists(AiCoachResponse result, int requiredCount, boolean hasAnyCoach) {
		return new AiCoachAutoResponse(Status.EXISTS, result, requiredCount, requiredCount, hasAnyCoach);
	}

	public static AiCoachAutoResponse notEligible(int newRecordsSinceLast, int requiredCount, boolean hasAnyCoach) {
		return new AiCoachAutoResponse(Status.NOT_ELIGIBLE, null, newRecordsSinceLast, requiredCount, hasAnyCoach);
	}

	public static AiCoachAutoResponse disabled(int requiredCount) {
		return new AiCoachAutoResponse(Status.DISABLED, null, 0, requiredCount, false);
	}

	public static AiCoachAutoResponse limit(int requiredCount, boolean hasAnyCoach) {
		return new AiCoachAutoResponse(Status.LIMIT, null, 0, requiredCount, hasAnyCoach);
	}
}
