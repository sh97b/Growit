package com.growit.roadmap.dto.request;

import jakarta.validation.constraints.NotNull;

public record RoadmapItemDoneRequest(@NotNull(message = "done 값을 보내 주세요") Boolean done) {
}
