package com.growit.ai.service;

import com.growit.ai.dto.CompareMetrics;
import java.time.LocalDate;
import java.util.List;

/**
 * ai_results.result에 그대로 저장되는 JSON 모양과 1:1로 대응한다(COMPARE).
 * schemaVersion이 null인 예전 결과는 interpretation 한 문단만 갖고, 2인 결과는 headline·good·gap·next를 갖는다
 * (이때 interpretation에는 headline을 같이 넣어 둔다).
 */
record StoredCompareResult(String interpretation, Long pastRecordId, Long currentRecordId, CompareMetrics metrics,
		LocalDate baseDate, boolean anchoredToLatest, boolean pastIsOldest, Integer schemaVersion, String headline,
		List<StoredCompareInsight> good, List<StoredCompareInsight> gap, List<StoredCompareIdea> next) {
}
