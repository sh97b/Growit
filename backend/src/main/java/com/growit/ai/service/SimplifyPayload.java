package com.growit.ai.service;

import java.util.List;

record SimplifyPayload(String originalTitle, Integer originalMinutes, String originalReason,
		List<ComparePayloadRecord> evidenceRecords) {
}
