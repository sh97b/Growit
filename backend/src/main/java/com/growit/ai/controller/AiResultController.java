package com.growit.ai.controller;

import com.growit.ai.dto.AiCoachResponse;
import com.growit.ai.dto.AiResultFeedbackRequest;
import com.growit.ai.dto.AiResultReactionRequest;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.ai.service.CoachService;
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
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COMPARE·COACH가 같은 ai_results 표를 쓰므로, id·type만으로 어느 쪽인지 알아내 해당 서비스로 넘긴다. */
@Tag(name = "AI", description = "AI 호출 공통")
@RestController
@RequestMapping("/api/ai/results")
public class AiResultController {

	private static final Set<String> VALID_FEEDBACK = Set.of("HELPFUL", "NOT_HELPFUL");
	private static final Set<String> VALID_REACTION = Set.of("TRIED", "REST_TODAY", "TOO_HARD");

	private final AiResultRepository aiResultRepository;
	private final CompareService compareService;
	private final CoachService coachService;
	private final CurrentUserProvider currentUserProvider;

	public AiResultController(AiResultRepository aiResultRepository, CompareService compareService,
			CoachService coachService, CurrentUserProvider currentUserProvider) {
		this.aiResultRepository = aiResultRepository;
		this.compareService = compareService;
		this.coachService = coachService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "이전 결과 목록 (최신순, 내 것만, 지금은 COACH만 지원)")
	@GetMapping
	public List<AiCoachResponse> list(@RequestParam String type, @RequestParam(required = false) Long hobbyId,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		if (!AiResultType.COACH.name().equals(type)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "type은 COACH만 지원해요");
		}
		return coachService.history(currentUserProvider.getCurrentUserId(), hobbyId, page == null ? 0 : page,
				size == null ? 20 : size);
	}

	@Operation(summary = "가장 최근 COACH 결과 (없으면 204)")
	@GetMapping("/latest")
	public ResponseEntity<AiCoachResponse> latest(@RequestParam String type) {
		if (!AiResultType.COACH.name().equals(type)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "type은 COACH만 지원해요");
		}
		AiCoachResponse result = coachService.latest(currentUserProvider.getCurrentUserId());
		return result == null ? ResponseEntity.status(HttpStatus.NO_CONTENT).build() : ResponseEntity.ok(result);
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
		if (type.equals(AiResultType.COACH.name())) {
			return coachService.getById(userId, id);
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
		if (type.equals(AiResultType.COACH.name())) {
			return coachService.submitFeedback(userId, id, request.value());
		}
		throw new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요");
	}

	@Operation(summary = "COACH의 다음 작은 행동에 대한 반응 (TRIED·REST_TODAY·TOO_HARD, 내 것만)")
	@ApiResponse(responseCode = "404", description = "내 결과가 아니거나 COACH가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PutMapping("/{id}/reaction")
	public AiCoachResponse submitReaction(@PathVariable Long id, @RequestBody AiResultReactionRequest request) {
		if (!VALID_REACTION.contains(request.value())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "value는 TRIED·REST_TODAY·TOO_HARD 중 하나여야 해요");
		}
		return coachService.submitReaction(currentUserProvider.getCurrentUserId(), id, request.value());
	}

	/** 이미 한 번 줄였으면 AI_ALREADY_SIMPLIFIED(409). 성공하면 reaction을 비워 다시 반응할 수 있게 한다. */
	@Operation(summary = "COACH의 다음 작은 행동을 더 작게 바꾸기 (결과당 1회, 내 것만)")
	@ApiResponse(responseCode = "404", description = "내 결과가 아니거나 COACH가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "AI_ALREADY_SIMPLIFIED: 이미 더 작게 바꿨음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/{id}/simplify")
	public AiCoachResponse simplify(@PathVariable Long id) {
		return coachService.simplify(currentUserProvider.getCurrentUserId(), id);
	}

	private String resultTypeOf(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return result.getResultType();
	}
}
