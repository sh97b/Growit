package com.growit.ai.service;

import java.util.List;

record CoachPayload(String mode, List<ComparePayloadHobby> hobbies, int daysSinceLastRecord,
		List<ComparePayloadRecord> records) {
}
