package com.growit.record.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorResponse;
import com.growit.record.dto.AnalysisFeedbackRequest;
import com.growit.record.dto.AnalysisTagsEditRequest;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
import com.growit.record.service.RecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기록", description = "활동 기록 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/records")
public class RecordController {

	private final RecordService recordService;
	private final CurrentUserProvider currentUserProvider;

	public RecordController(RecordService recordService, CurrentUserProvider currentUserProvider) {
		this.recordService = recordService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "기록 목록 조회 (최신순, 분야·기간·페이지로 필터링)")
	@GetMapping
	public RecordListResponse list(
			@RequestParam(required = false) Long hobbyId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return recordService.list(currentUserProvider.getCurrentUserId(), hobbyId, from, to, page, size);
	}

	@Operation(summary = "기록 상세 조회 (같은 분야의 이전·다음 기록 포함)")
	@ApiResponse(responseCode = "404", description = "내 기록이 아니거나 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/{id}")
	public RecordResponse get(@PathVariable Long id) {
		return recordService.get(currentUserProvider.getCurrentUserId(), id);
	}

	/** 날짜가 서버 기준(Asia/Seoul) 오늘보다 미래거나 분야가 보관됐으면 VALIDATION_ERROR(400). */
	@Operation(summary = "기록 등록")
	@ApiResponse(responseCode = "400", description = "입력값 오류 (미래 날짜, 내용 비어 있음 등)",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<RecordResponse> create(@Valid @RequestBody RecordCreateRequest request) {
		RecordResponse response = recordService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/** minutes·difficulty·feeling을 null로 보내면 비우고, 안 보내면 그대로 둔다. */
	@Operation(summary = "기록 수정 (보낸 칸만 수정, 값을 null로 보내면 minutes·difficulty·feeling은 비움, ai_analysis는 초기화됨)")
	@ApiResponse(responseCode = "400", description = "입력값 오류 (미래 날짜, 내용 비어 있음 등)",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "내 기록이 아니거나 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PatchMapping("/{id}")
	public RecordResponse update(@PathVariable Long id, @RequestBody RecordUpdateRequest request) {
		return recordService.update(currentUserProvider.getCurrentUserId(), id, request);
	}

	@Operation(summary = "기록 삭제")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		recordService.delete(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}

	/** 이미 PENDING이면 새로 요청하지 않고 그대로 돌려준다. AI가 꺼져 있으면 AI_DISABLED(409). */
	@Operation(summary = "기록 정리 다시 요청 (PENDING이면 그대로 반환)")
	@ApiResponse(responseCode = "409", description = "AI_DISABLED: 서버 또는 내 AI 설정이 꺼져 있음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/{id}/analysis")
	public ResponseEntity<RecordResponse> reanalyze(@PathVariable Long id) {
		RecordResponse response = recordService.reanalyze(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	/** 분석이 DONE 상태가 아니면 ANALYSIS_NOT_READY(409). */
	@Operation(summary = "기록 정리 결과에 피드백 남기기 (change·tags 중 하나 이상)")
	@ApiResponse(responseCode = "409", description = "ANALYSIS_NOT_READY: 분석이 DONE 상태가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PutMapping("/{id}/analysis/feedback")
	public RecordResponse submitFeedback(@PathVariable Long id, @RequestBody AnalysisFeedbackRequest request) {
		return recordService.submitFeedback(currentUserProvider.getCurrentUserId(), id, request);
	}

	/** 분석이 DONE 상태가 아니면 ANALYSIS_NOT_READY(409). */
	@Operation(summary = "태그 직접 수정 (1~3개, 각 1~12자, tagsFeedback은 EDITED로 바뀜)")
	@ApiResponse(responseCode = "409", description = "ANALYSIS_NOT_READY: 분석이 DONE 상태가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PatchMapping("/{id}/analysis/tags")
	public RecordResponse editTags(@PathVariable Long id, @RequestBody AnalysisTagsEditRequest request) {
		return recordService.editTags(currentUserProvider.getCurrentUserId(), id, request);
	}
}
