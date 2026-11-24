package com.growit.roadmap.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/** position은 항목 안에서 1부터 센 순서. payload는 종류별 JSON(LINK에는 서버가 계산한 host가 들어 있다). */
public record BlockResponse(Long id, Long itemId, String kind, int position, JsonNode payload,
		LocalDateTime createdAt, LocalDateTime updatedAt) {
}
