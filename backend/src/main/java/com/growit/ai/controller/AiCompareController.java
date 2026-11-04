package com.growit.ai.controller;

import com.growit.ai.dto.AiCompareResponse;
import com.growit.ai.dto.CompareRequest;
import com.growit.ai.service.CompareService;
import com.growit.auth.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 호출 공통")
@RestController
@RequestMapping("/api/ai")
public class AiCompareController {

	private final CompareService compareService;
	private final CurrentUserProvider currentUserProvider;

	public AiCompareController(CompareService compareService, CurrentUserProvider currentUserProvider) {
		this.compareService = compareService;
		this.currentUserProvider = currentUserProvider;
	}

	/** 분야 기록이 3개 미만이거나 비교할 과거 시점 기록이 없으면 locked=true로 응답하고 AI를 부르지 않는다. */
	@Operation(summary = "나의 변화(두 기록 비교) 요청")
	@PostMapping("/compare")
	public AiCompareResponse compare(@RequestBody CompareRequest request) {
		return compareService.compare(currentUserProvider.getCurrentUserId(), request);
	}
}
