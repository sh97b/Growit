package com.growit.record.dto;

import com.growit.record.entity.AiStatus;
import com.growit.record.entity.Feeling;
import java.time.LocalDate;

public record RecordResponse(
		Long id,
		Long hobbyId,
		LocalDate recordDate,
		String content,
		Integer minutes,
		String difficulty,
		Feeling feeling,
		boolean isMilestone,
		AiStatus aiStatus,
		RecordAnalysisResponse analysis,
		RecordNeighbor previousRecord,
		RecordNeighbor nextRecord,
		String feelingLabel,
		String feelingDirection,
		/** 분석이 FAILED일 때만, 하루 한도로 건너뛴 경우 "LIMIT". 그 밖에는 null. */
		String aiFailReason) {

	public RecordResponse(Long id, Long hobbyId, LocalDate recordDate, String content, Integer minutes,
			String difficulty, Feeling feeling, boolean isMilestone, AiStatus aiStatus,
			RecordAnalysisResponse analysis, RecordNeighbor previousRecord, RecordNeighbor nextRecord,
			String feelingLabel, String feelingDirection) {
		this(id, hobbyId, recordDate, content, minutes, difficulty, feeling, isMilestone, aiStatus, analysis,
				previousRecord, nextRecord, feelingLabel, feelingDirection, null);
	}

	public RecordResponse(Long id, Long hobbyId, LocalDate recordDate, String content, Integer minutes,
			String difficulty, Feeling feeling, boolean isMilestone, AiStatus aiStatus,
			RecordAnalysisResponse analysis, RecordNeighbor previousRecord, RecordNeighbor nextRecord) {
		this(id, hobbyId, recordDate, content, minutes, difficulty, feeling, isMilestone, aiStatus, analysis,
				previousRecord, nextRecord, null, null, null);
	}
}
