package com.growit.ai.controller;

import com.growit.ai.dto.AiResultFeedbackRequest;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.ai.service.CompareService;
import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * COMPARE·COACH가 같은 ai_results 표를 쓰므로, id만으로 어느 쪽인지 알아내 해당 서비스로 넘긴다.
 * COACH 쪽 분기는 3단계에서 CoachService가 만들어지면 추가한다.
 */
@Tag(name = "AI", description = "AI 호출 공통")
@RestController
@RequestMapping("/api/ai/results")
public class AiResultController {

	private static final Set<String> VALID_FEEDBACK = Set.of("HELPFUL", "NOT_HELPFUL");

	private final AiResultRepository aiResultRepository;
	private final CompareService compareService;
	private final CurrentUserProvider currentUserProvider;

	public AiResultController(AiResultRepository aiResultRepository, CompareService compareService,
			CurrentUserProvider currentUserProvider) {
		this.aiResultRepository = aiResultRepository;
		this.compareService = compareService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "AI 결과 하나 조회 (내 것만)")
	@ApiResponse(responseCode = "404", description = "내 결과가 아니거나 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/{id}")
	public Object get(@PathVariable Long id) {
		Long userId = currentUserProvider.getCurrentUserId();
		String type = resultTypeOf(userId, id);
		if (type.equals(AiResultType.COMPARE.name())) {
			return compareService.getById(userId, id);
		}
		throw new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요");
	}

	@Operation(summary = "AI 결과 해석에 대한 피드백 (HELPFUL 또는 NOT_HELPFUL, 내 것만)")
	@ApiResponse(responseCode = "404", description = "내 결과가 아니거나 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PutMapping("/{id}/feedback")
	public Object submitFeedback(@PathVariable Long id, @RequestBody AiResultFeedbackRequest request) {
		if (!VALID_FEEDBACK.contains(request.value())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "value는 HELPFUL 또는 NOT_HELPFUL이어야 해요");
		}
		Long userId = currentUserProvider.getCurrentUserId();
		String type = resultTypeOf(userId, id);
		if (type.equals(AiResultType.COMPARE.name())) {
			return compareService.submitFeedback(userId, id, request.value());
		}
		throw new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요");
	}

	private String resultTypeOf(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return result.getResultType();
	}
}
