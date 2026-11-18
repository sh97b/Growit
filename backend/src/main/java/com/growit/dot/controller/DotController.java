package com.growit.dot.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorResponse;
import com.growit.dot.dto.DotCurrentResponse;
import com.growit.dot.dto.DotGoalResponse;
import com.growit.dot.dto.DotGoalStartRequest;
import com.growit.dot.dto.DotPresetResponse;
import com.growit.dot.service.DotGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "도트 그림", description = "기록한 날마다 한 칸씩 칠하는 도트 그림 목표")
@RestController
@RequestMapping("/api/dots")
public class DotController {

	private final DotGoalService dotGoalService;
	private final CurrentUserProvider currentUserProvider;

	public DotController(DotGoalService dotGoalService, CurrentUserProvider currentUserProvider) {
		this.dotGoalService = dotGoalService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "고를 수 있는 기본 그림 목록")
	@GetMapping("/presets")
	public List<DotPresetResponse> presets() {
		return dotGoalService.presets();
	}

	@Operation(summary = "진행 중인 그림과 연출을 아직 안 본 완성 그림 (빠진 날은 이때 채워 넣음)")
	@GetMapping("/current")
	public DotCurrentResponse current() {
		return dotGoalService.current(currentUserProvider.getCurrentUserId());
	}

	/** 이미 진행 중인 그림이 있으면 DOT_GOAL_ACTIVE(409), 목표 일수·그림이 올바르지 않으면 VALIDATION_ERROR(400). */
	@Operation(summary = "도트 그림 시작 (그림 칸 수는 목표 일수와 같게 서버가 만든다)")
	@ApiResponse(responseCode = "400", description = "목표 일수 또는 그림 오류",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "이미 진행 중인 그림이 있음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/goals")
	public ResponseEntity<DotGoalResponse> start(@Valid @RequestBody DotGoalStartRequest request) {
		DotGoalResponse response = dotGoalService.start(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/** 진행 중인 내 그림이 아니면 NOT_FOUND(404). */
	@Operation(summary = "진행 중인 도트 그림 중단 (칠한 기록은 보관만 하고 화면에서는 숨김)")
	@ApiResponse(responseCode = "404", description = "진행 중인 내 그림이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/goals/{id}/stop")
	public ResponseEntity<Void> stop(@PathVariable Long id) {
		dotGoalService.stop(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "도감 (완성한 그림만)")
	@GetMapping("/goals")
	public List<DotGoalResponse> album() {
		return dotGoalService.album(currentUserProvider.getCurrentUserId());
	}

	@Operation(summary = "도트 그림 상세 (중단한 그림은 404)")
	@ApiResponse(responseCode = "404", description = "내 그림이 아니거나 중단한 그림",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/goals/{id}")
	public DotGoalResponse detail(@PathVariable Long id) {
		return dotGoalService.detail(currentUserProvider.getCurrentUserId(), id);
	}

	@Operation(summary = "완성 연출을 봤다고 표시")
	@ApiResponse(responseCode = "404", description = "완성한 내 그림이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/goals/{id}/completion-seen")
	public ResponseEntity<Void> completionSeen(@PathVariable Long id) {
		dotGoalService.markCompletionSeen(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}
}
