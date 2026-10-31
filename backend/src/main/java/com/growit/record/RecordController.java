package com.growit.record;

import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorResponse;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
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

	@Operation(summary = "기록 등록")
	@ApiResponse(responseCode = "400", description = "입력값 오류 (미래 날짜, 내용 비어 있음 등)",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<RecordResponse> create(@Valid @RequestBody RecordCreateRequest request) {
		RecordResponse response = recordService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

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
}
