package com.growit.ai.dto;

import java.util.List;

public record CompareMetrics(MetricPair recordedDays, MetricPair totalMinutes, List<TopicCount> topTags) {
}
