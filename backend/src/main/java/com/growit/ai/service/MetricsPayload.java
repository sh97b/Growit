package com.growit.ai.service;

import java.util.List;

record MetricsPayload(List<MetricsWeekPoint> weeks, List<ViewPayloadRecord> records) {
}
