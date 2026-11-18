package com.growit.record.dto;

import com.growit.record.entity.FeelingDirection;

public record CustomFeelingResponse(Long id, String label, FeelingDirection direction) {
}
