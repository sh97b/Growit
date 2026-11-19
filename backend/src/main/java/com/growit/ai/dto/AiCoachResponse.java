package com.growit.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiCoachResponse(
		boolean locked,
		Integer requiredCount,
		Integer currentCount,
		Long id,
		String mode,
		LocalDate startDate,
		LocalDate endDate,
		LocalDateTime createdAt,
		boolean reused,
		String periodSummary,
		List<CoachTextItemResponse> changes,
		List<CoachTextItemResponse> repeatedDifficulties,
		CoachActionResponse nextAction,
		CoachActionResponse comebackAction,
		Integer recordsRead,
		String feedback,
		String reaction,
		/** ACTIVE·CELEBRATE·RESTING·EXPIRED. 홈에서 카드를 어떻게 보여줄지 서버가 판단해 내려준다. */
		String cardState,
		/** 주간 자동 회고(isAuto)이고 아직 한 번도 안 열어봤을 때만 true. 홈의 "새 회고 도착" 표시에 쓴다. */
		boolean isNew,
		/** 2면 새 구성(돌아보기·질문·방향 후보), 없으면 예전 모양(다음 작은 행동 포함)이다. */
		Integer schemaVersion,
		List<CoachTextItemResponse> flow,
		List<CoachTextItemResponse> resolvedDifficulties,
		List<CoachTextItemResponse> questions,
		List<CoachTextItemResponse> directions,
		CoachStatsResponse stats,
		List<CoachDirectionResponse> directionPlans) {

	/** 예전 모양의 결과를 만들 때 쓴다. */
	public AiCoachResponse(boolean locked, Integer requiredCount, Integer currentCount, Long id, String mode,
			LocalDate startDate, LocalDate endDate, LocalDateTime createdAt, boolean reused, String periodSummary,
			List<CoachTextItemResponse> changes, List<CoachTextItemResponse> repeatedDifficulties,
			CoachActionResponse nextAction, CoachActionResponse comebackAction, Integer recordsRead, String feedback,
			String reaction, String cardState, boolean isNew) {
		this(locked, requiredCount, currentCount, id, mode, startDate, endDate, createdAt, reused, periodSummary,
				changes, repeatedDifficulties, nextAction, comebackAction, recordsRead, feedback, reaction, cardState,
				isNew, null, null, null, null, null, null, null);
	}

	public static AiCoachResponse locked(int requiredCount, int currentCount) {
		return new AiCoachResponse(true, requiredCount, currentCount, null, null, null, null, null, false, null,
				null, null, null, null, null, null, null, null, false);
	}
}
