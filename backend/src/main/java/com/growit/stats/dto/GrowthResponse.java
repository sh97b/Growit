package com.growit.stats.dto;

import java.time.LocalDate;
import java.util.List;

/** 성장 화면 하나가 쓰는 집계 전부. AI 호출 없이 기록에서만 계산한다. */
public record GrowthResponse(
		Summary summary,
		List<Weekly> weekly,
		List<ByHobby> byHobby,
		List<Integer> weekday,
		Feelings feelings,
		List<WeekTagCount> topics,
		List<Milestone> milestones) {

	/** firstRecordDate는 기간과 상관없는 첫 기록일이다(분야를 골랐으면 그 분야의 첫 기록일). */
	public record Summary(int recordedDays, long records, long minutes, LocalDate firstRecordDate) {
	}

	public record Weekly(LocalDate start, int days, long minutes) {
	}

	public record ByHobby(Long hobbyId, String name, long records, long minutes, LocalDate firstDate,
			boolean archived) {
	}

	public record Feelings(List<Bucket> buckets, List<ByFeeling> byFeeling, Totals totals) {
	}

	/** 31일 이하는 하루씩(start==end), 그보다 길면 월요일 시작 주 단위다. */
	public record Bucket(LocalDate start, LocalDate end, long good, long neutral, long hard, long none) {
	}

	/** direction은 GOOD/NEUTRAL/HARD. 감정을 안 고른 기록은 여기 들어가지 않는다. */
	public record ByFeeling(String label, String direction, boolean builtIn, long count) {
	}

	public record Totals(long good, long neutral, long hard, long none) {
	}

	public record Milestone(Long recordId, LocalDate date, String hobbyName, String preview) {
	}
}
