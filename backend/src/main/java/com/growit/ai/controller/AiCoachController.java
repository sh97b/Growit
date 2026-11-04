package com.growit.ai.controller;

import com.growit.ai.dto.AiCoachResponse;
import com.growit.ai.dto.CoachRequest;
import com.growit.ai.service.CoachService;
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
public class AiCoachController {

	private final CoachService coachService;
	private final CurrentUserProvider currentUserProvider;

	public AiCoachController(CoachService coachService, CurrentUserProvider currentUserProvider) {
		this.coachService = coachService;
		this.currentUserProvider = currentUserProvider;
	}

	/** 기간 내 기록이 5개 미만이면 locked=true로 응답하고 AI를 부르지 않는다. */
	@Operation(summary = "AI 회고 요청 (hobbyId 없으면 전체 분야)")
	@PostMapping("/coach")
	public AiCoachResponse coach(@RequestBody CoachRequest request) {
		return coachService.coach(currentUserProvider.getCurrentUserId(), request);
	}
}
