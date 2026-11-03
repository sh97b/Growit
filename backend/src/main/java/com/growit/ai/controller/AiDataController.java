package com.growit.ai.controller;

import com.growit.ai.service.AiDataResetService;
import com.growit.auth.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "AI 호출 공통")
@RestController
@RequestMapping("/api/ai")
public class AiDataController {

	private final AiDataResetService aiDataResetService;
	private final CurrentUserProvider currentUserProvider;

	public AiDataController(AiDataResetService aiDataResetService, CurrentUserProvider currentUserProvider) {
		this.aiDataResetService = aiDataResetService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내 AI 데이터 초기화 (ai_results 삭제, 기록의 분석 결과를 비움. 기록 본문은 그대로)")
	@DeleteMapping("/data")
	public ResponseEntity<Void> deleteMyData() {
		aiDataResetService.resetMyData(currentUserProvider.getCurrentUserId());
		return ResponseEntity.noContent().build();
	}
}
