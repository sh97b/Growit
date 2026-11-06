package com.growit.ai.controller;

import com.growit.ai.dto.AiViewResponse;
import com.growit.ai.dto.MetricsRequest;
import com.growit.ai.dto.WeeklyRequest;
import com.growit.ai.service.MetricsService;
import com.growit.ai.service.WeeklyService;
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
public class AiViewController {

	private final WeeklyService weeklyService;
	private final MetricsService metricsService;
	private final CurrentUserProvider currentUserProvider;

	public AiViewController(WeeklyService weeklyService, MetricsService metricsService,
			CurrentUserProvider currentUserProvider) {
		this.weeklyService = weeklyService;
		this.metricsService = metricsService;
		this.currentUserProvider = currentUserProvider;
	}

	/** 그 주 기록이 0개면 empty=true로 응답하고 AI를 부르지 않는다. */
	@Operation(summary = "이 주의 한 줄 요청")
	@PostMapping("/weekly")
	public AiViewResponse weekly(@RequestBody WeeklyRequest request) {
		return weeklyService.weekly(currentUserProvider.getCurrentUserId(), request);
	}

	/** 기록이 있는 주가 2개 미만이면 locked=true로 응답하고 AI를 부르지 않는다. */
	@Operation(summary = "지표 해석 요청")
	@PostMapping("/metrics")
	public AiViewResponse metrics(@RequestBody MetricsRequest request) {
		return metricsService.metrics(currentUserProvider.getCurrentUserId(), request);
	}
}
