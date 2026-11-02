package com.growit.ai.controller;

import com.growit.ai.dto.AiUsageResponse;
import com.growit.ai.service.AiUsageService;
import com.growit.auth.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 호출 공통")
@RestController
@RequestMapping("/api/ai")
public class AiUsageController {

	private final AiUsageService aiUsageService;
	private final CurrentUserProvider currentUserProvider;

	public AiUsageController(AiUsageService aiUsageService, CurrentUserProvider currentUserProvider) {
		this.aiUsageService = aiUsageService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내 AI 사용량 조회 (오늘, 변화 비교·회고 합산 기준)")
	@GetMapping("/usage")
	public AiUsageResponse usage() {
		return aiUsageService.usage(currentUserProvider.getCurrentUserId());
	}
}
