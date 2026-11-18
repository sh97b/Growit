package com.growit.record.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorResponse;
import com.growit.record.dto.CustomFeelingRequest;
import com.growit.record.dto.CustomFeelingResponse;
import com.growit.record.service.CustomFeelingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "느낌", description = "직접 추가한 느낌 목록")
@RestController
@RequestMapping("/api/feelings")
public class CustomFeelingController {

	private final CustomFeelingService customFeelingService;
	private final CurrentUserProvider currentUserProvider;

	public CustomFeelingController(CustomFeelingService customFeelingService, CurrentUserProvider currentUserProvider) {
		this.customFeelingService = customFeelingService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내가 직접 추가한 느낌 목록")
	@GetMapping
	public List<CustomFeelingResponse> list() {
		return customFeelingService.list(currentUserProvider.getCurrentUserId());
	}

	/** 이름(1~8자, 한글·영문·숫자·공백)이 올바르지 않거나 중복이거나 12개를 넘으면 VALIDATION_ERROR(400). */
	@Operation(summary = "느낌 직접 추가 (이름과 기분 방향)")
	@ApiResponse(responseCode = "400", description = "이름·개수 검증 실패",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<CustomFeelingResponse> create(@Valid @RequestBody CustomFeelingRequest request) {
		CustomFeelingResponse response = customFeelingService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "직접 추가한 느낌 삭제 (이미 쓴 기록의 느낌은 그대로)")
	@ApiResponse(responseCode = "404", description = "내 느낌이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		customFeelingService.delete(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}
}
