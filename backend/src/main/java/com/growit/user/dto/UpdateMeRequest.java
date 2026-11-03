package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateMeRequest(
		@Schema(example = "서현") String displayName,
		@Schema(example = "true", description = "꺼도 이미 PENDING인 분석은 그대로 두고, 새 기록부터 건너뜀") Boolean aiEnabled) {
}
