package com.growit.hobby.dto;

import org.openapitools.jackson.nullable.JsonNullable;

public record HobbyUpdateRequest(String name, JsonNullable<String> goal, Boolean archived) {
}
