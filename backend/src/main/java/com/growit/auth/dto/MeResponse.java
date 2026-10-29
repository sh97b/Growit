package com.growit.auth.dto;

import java.time.LocalDateTime;

public record MeResponse(Long id, String email, String displayName, LocalDateTime createdAt) {
}
