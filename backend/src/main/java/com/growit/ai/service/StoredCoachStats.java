package com.growit.ai.service;

import java.util.List;

/** 서버가 기록에서 직접 센 숫자(AI가 만든 값이 아니다). 회고를 만들 때의 값을 그대로 보관한다. */
record StoredCoachStats(int recordCount, int recordedDays, int totalMinutes, List<StoredFeelingCount> feelings) {

	record StoredFeelingCount(String label, int count) {
	}
}
