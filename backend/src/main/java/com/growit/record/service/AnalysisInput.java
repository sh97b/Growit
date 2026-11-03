package com.growit.record.service;

import java.util.Set;

record AnalysisInput(AnalysisPayload payload, Set<Long> allowedHistoryIds) {
}
