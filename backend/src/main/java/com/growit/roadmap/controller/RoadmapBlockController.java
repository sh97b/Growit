package com.growit.roadmap.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorResponse;
import com.growit.roadmap.dto.request.BlockCreateRequest;
import com.growit.roadmap.dto.request.BlockOrderRequest;
import com.growit.roadmap.dto.request.BlockUpdateRequest;
import com.growit.roadmap.dto.response.AiHowtoResponse;
import com.growit.roadmap.dto.response.BlockResponse;
import com.growit.roadmap.service.RoadmapBlockService;
import com.growit.roadmap.service.RoadmapHowtoService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로드맵 블록", description = "로드맵 항목에 붙이는 메모·링크·타이머·해 보는 방법·검색·하위 체크 블록")
@RestController
@RequestMapping("/api")
public class RoadmapBlockController {

	private final RoadmapBlockService blockService;
	private final RoadmapHowtoService howtoService;
	private final CurrentUserProvider currentUserProvider;

	public RoadmapBlockController(RoadmapBlockService blockService, RoadmapHowtoService howtoService,
			CurrentUserProvider currentUserProvider) {
		this.blockService = blockService;
		this.howtoService = howtoService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "항목의 블록 목록(보관된 로드맵도 읽기 가능)")
	@ApiResponse(responseCode = "404", description = "내 항목이 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/roadmap-items/{itemId}/blocks")
	public List<BlockResponse> list(@PathVariable Long itemId) {
		return blockService.list(currentUserProvider.getCurrentUserId(), itemId);
	}

	@Operation(summary = "블록 추가(항목당 12개, 로드맵당 120개, AI_HOWTO는 전용 API)")
	@ApiResponse(responseCode = "400", description = "종류별 검증 실패 또는 한도 초과",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/roadmap-items/{itemId}/blocks")
	public ResponseEntity<BlockResponse> create(@PathVariable Long itemId, @Valid @RequestBody BlockCreateRequest request) {
		BlockResponse created = blockService.create(currentUserProvider.getCurrentUserId(), itemId, request.kind(),
				request.payload());
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@Operation(summary = "블록 수정(종류는 바꿀 수 없다)")
	@PatchMapping("/blocks/{blockId}")
	public BlockResponse update(@PathVariable Long blockId, @RequestBody BlockUpdateRequest request) {
		return blockService.update(currentUserProvider.getCurrentUserId(), blockId, request.payload());
	}

	@Operation(summary = "블록 삭제")
	@DeleteMapping("/blocks/{blockId}")
	public ResponseEntity<Void> delete(@PathVariable Long blockId) {
		blockService.delete(currentUserProvider.getCurrentUserId(), blockId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "블록 순서 변경(그 항목의 블록 id 전체를 원하는 순서로)")
	@PutMapping("/roadmap-items/{itemId}/blocks/order")
	public List<BlockResponse> reorder(@PathVariable Long itemId, @RequestBody BlockOrderRequest request) {
		return blockService.reorder(currentUserProvider.getCurrentUserId(), itemId, request.ids());
	}

	@Operation(summary = "해 보는 방법 블록을 AI로 만들기(다시 만들면 덮어쓴다, 하루 3회까지)")
	@ApiResponse(responseCode = "400", description = "방법을 만들지 못함",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429", description = "AI 하루 한도 또는 다시 만들기 한도 초과",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/roadmap-items/{itemId}/blocks/ai-howto")
	public AiHowtoResponse aiHowto(@PathVariable Long itemId) {
		return howtoService.generate(currentUserProvider.getCurrentUserId(), itemId);
	}
}
