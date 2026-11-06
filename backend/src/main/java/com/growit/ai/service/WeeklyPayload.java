package com.growit.ai.service;

import java.util.List;

record WeeklyPayload(String weekStart, List<ViewPayloadRecord> records) {
}
