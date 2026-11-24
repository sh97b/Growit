package com.growit.roadmap.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/** kind는 MEMO/LINK/TIMER/SEARCH/SUBLIST(AI_HOWTO는 전용 API). payload는 종류별로 서버가 검증한다. */
public record BlockCreateRequest(@NotBlank(message = "블록 종류를 골라 주세요") String kind, JsonNode payload) {
}
