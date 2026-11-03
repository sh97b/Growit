package com.growit.record.dto;

import com.growit.record.entity.ChangeFeedback;
import com.growit.record.entity.TagsFeedbackChoice;

public record AnalysisFeedbackRequest(ChangeFeedback change, TagsFeedbackChoice tags) {
}
