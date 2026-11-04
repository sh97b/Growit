package com.growit.ai.service;

import com.growit.ai.dto.CompareMetrics;

/** ai_results.result에 그대로 저장되는 JSON 모양과 1:1로 대응한다(COMPARE). */
record StoredCompareResult(String interpretation, Long pastRecordId, Long currentRecordId, CompareMetrics metrics) {
}
