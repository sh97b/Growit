package com.growit.ai.controller;

import com.growit.ai.dto.CompareBasisResponse;
import com.growit.ai.dto.CompareRequest;
import com.growit.ai.service.CompareService;
import com.growit.auth.security.CurrentUserProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "변화", description = "나의 변화 기본 데이터")
@RestController
@RequestMapping("/api/compare")
public class CompareBasisController {

	private final CompareService compareService;
	private final CurrentUserProvider currentUserProvider;

	public CompareBasisController(CompareService compareService, CurrentUserProvider currentUserProvider) {
		this.compareService = compareService;
		this.currentUserProvider = currentUserProvider;
	}

	/** AI를 부르지 않는다. 비교 요청(POST /api/ai/compare)과 같은 분야·기간 값으로 이전·최근 기록과 숫자를 즉시 돌려준다. */
	@Operation(summary = "나의 변화 기본 데이터(이전·최근 기록, 숫자) - AI 없이 즉시")
	@GetMapping("/basis")
	public CompareBasisResponse basis(@RequestParam Long hobbyId, @RequestParam(defaultValue = "1W") String compareTo) {
		return compareService.basis(currentUserProvider.getCurrentUserId(), new CompareRequest(hobbyId, compareTo));
	}
}
