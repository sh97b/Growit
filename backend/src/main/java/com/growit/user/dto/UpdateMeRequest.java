package com.growit.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UpdateMeRequest(@Schema(example = "서현") String displayName) {
}
