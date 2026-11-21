package com.growit.roadmap.dto.request;

import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

/** targetDate를 null로 보내면 비운다. 보내지 않으면 그대로 둔다. */
public record RoadmapUpdateRequest(JsonNullable<LocalDate> targetDate) {
}
