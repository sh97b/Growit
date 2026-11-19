package com.growit.record.service;

import java.util.Set;

/** recordId·corpus는 nextAction 검증용(근거 id 허용 범위, 입력에 나온 낱말 모음)이다. */
record AnalysisInput(AnalysisPayload payload, Set<Long> allowedHistoryIds, Long recordId, String corpus) {
}
