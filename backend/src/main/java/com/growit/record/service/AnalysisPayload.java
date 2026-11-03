package com.growit.record.service;

import java.util.List;

record AnalysisPayload(AnalysisHobbyInput hobby, AnalysisRecordInput record, List<AnalysisHistoryItem> history) {
}
