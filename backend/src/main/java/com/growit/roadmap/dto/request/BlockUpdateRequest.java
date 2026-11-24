package com.growit.roadmap.dto.request;

import com.fasterxml.jackson.databind.JsonNode;

public record BlockUpdateRequest(JsonNode payload) {
}
