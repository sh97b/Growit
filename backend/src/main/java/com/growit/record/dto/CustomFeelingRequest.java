package com.growit.record.dto;

import com.growit.record.entity.FeelingDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CustomFeelingRequest(
		@Schema(example = "설렘") String label,
		@Schema(example = "GOOD", description = "GOOD(좋아요), NEUTRAL(보통이에요), HARD(힘들어요)")
		@NotNull(message = "기분 방향을 골라 주세요") FeelingDirection direction) {
}
