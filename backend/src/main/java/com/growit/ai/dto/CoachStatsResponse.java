package com.growit.ai.dto;

import java.util.List;

/** 기록한 날·시간·느낌 분포처럼 서버가 센 숫자. 화면은 이 값을 그대로 채운다. */
public record CoachStatsResponse(int recordCount, int recordedDays, int totalMinutes,
		List<CoachFeelingCount> feelings) {
}
