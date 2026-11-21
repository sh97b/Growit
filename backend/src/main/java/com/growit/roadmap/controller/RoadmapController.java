package com.growit.roadmap.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorResponse;
import com.growit.roadmap.dto.request.RoadmapCreateRequest;
import com.growit.roadmap.dto.request.RoadmapItemCreateRequest;
import com.growit.roadmap.dto.request.RoadmapItemDoneRequest;
import com.growit.roadmap.dto.request.RoadmapItemUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapStepCreateRequest;
import com.growit.roadmap.dto.request.RoadmapStepUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapUpdateRequest;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.service.RoadmapGenerationService;
import com.growit.roadmap.service.RoadmapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로드맵", description = "분야 목표를 이루기까지의 단계별 로드맵(날짜별 일정 없음)")
@RestController
@RequestMapping("/api")
public class RoadmapController {

	private final RoadmapService roadmapService;
	private final RoadmapGenerationService generationService;
	private final CurrentUserProvider currentUserProvider;

	public RoadmapController(RoadmapService roadmapService, RoadmapGenerationService generationService,
			CurrentUserProvider currentUserProvider) {
		this.roadmapService = roadmapService;
		this.generationService = generationService;
		this.currentUserProvider = currentUserProvider;
	}

	/** 진행 중인 로드맵이 없으면 본문이 JSON null이다. 내 분야가 아니면 NOT_FOUND(404). */
	@Operation(summary = "분야의 진행 중인 로드맵 조회 (없으면 null)")
	@ApiResponse(responseCode = "404", description = "내 활동 분야가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/hobbies/{hobbyId}/roadmap")
	public ResponseEntity<Object> get(@PathVariable Long hobbyId) {
		RoadmapResponse roadmap = roadmapService.getActive(currentUserProvider.getCurrentUserId(), hobbyId);
		if (roadmap == null) {
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("null");
		}
		return ResponseEntity.ok(roadmap);
	}

	/** 목표가 비어 있으면 400, 진행 중인 로드맵이 있으면 ROADMAP_ACTIVE(409), 하루 한도를 넘으면 429. */
	@Operation(summary = "AI 로드맵 생성")
	@ApiResponse(responseCode = "400", description = "목표 없음 또는 생성 실패",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "이미 진행 중인 로드맵이 있음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/hobbies/{hobbyId}/roadmap")
	public ResponseEntity<RoadmapResponse> create(@PathVariable Long hobbyId,
			@Valid @RequestBody(required = false) RoadmapCreateRequest request) {
		RoadmapResponse response = generationService.create(currentUserProvider.getCurrentUserId(), hobbyId,
				request == null ? null : request.targetDate());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "로드맵 목표일 수정 (null로 보내면 비움)")
	@PatchMapping("/roadmaps/{id}")
	public RoadmapResponse update(@PathVariable Long id, @RequestBody RoadmapUpdateRequest request) {
		return roadmapService.updateTarget(currentUserProvider.getCurrentUserId(), id, request);
	}

	@Operation(summary = "로드맵 보관 (새로 만들 수 있게 됨)")
	@PostMapping("/roadmaps/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable Long id) {
		roadmapService.archive(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "단계 추가")
	@PostMapping("/roadmaps/{id}/steps")
	public ResponseEntity<RoadmapResponse> addStep(@PathVariable Long id,
			@Valid @RequestBody RoadmapStepCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(roadmapService.addStep(currentUserProvider.getCurrentUserId(), id, request));
	}

	@Operation(summary = "단계 수정 (제목·기간 힌트·위/아래 이동)")
	@PatchMapping("/roadmap-steps/{id}")
	public RoadmapResponse updateStep(@PathVariable Long id, @Valid @RequestBody RoadmapStepUpdateRequest request) {
		return roadmapService.updateStep(currentUserProvider.getCurrentUserId(), id, request);
	}

	@Operation(summary = "단계 삭제 (안의 항목도 함께)")
	@DeleteMapping("/roadmap-steps/{id}")
	public RoadmapResponse deleteStep(@PathVariable Long id) {
		return roadmapService.deleteStep(currentUserProvider.getCurrentUserId(), id);
	}

	@Operation(summary = "항목 추가")
	@PostMapping("/roadmap-steps/{id}/items")
	public ResponseEntity<RoadmapResponse> addItem(@PathVariable Long id,
			@Valid @RequestBody RoadmapItemCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(roadmapService.addItem(currentUserProvider.getCurrentUserId(), id, request));
	}

	@Operation(summary = "항목 수정 (문장·위/아래 이동)")
	@PatchMapping("/roadmap-items/{id}")
	public RoadmapResponse updateItem(@PathVariable Long id, @Valid @RequestBody RoadmapItemUpdateRequest request) {
		return roadmapService.updateItem(currentUserProvider.getCurrentUserId(), id, request);
	}

	@Operation(summary = "항목 삭제")
	@DeleteMapping("/roadmap-items/{id}")
	public RoadmapResponse deleteItem(@PathVariable Long id) {
		return roadmapService.deleteItem(currentUserProvider.getCurrentUserId(), id);
	}

	@Operation(summary = "항목 체크·체크 해제")
	@PutMapping("/roadmap-items/{id}/done")
	public RoadmapResponse setDone(@PathVariable Long id, @Valid @RequestBody RoadmapItemDoneRequest request) {
		return roadmapService.setItemDone(currentUserProvider.getCurrentUserId(), id, request);
	}
}
