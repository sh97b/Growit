package com.growit.hobby;

import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorResponse;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import com.growit.hobby.dto.HobbyUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "활동 분야", description = "활동 분야 등록·조회·수정·삭제")
@RestController
@RequestMapping("/api/hobbies")
public class HobbyController {

	private final HobbyService hobbyService;
	private final CurrentUserProvider currentUserProvider;

	public HobbyController(HobbyService hobbyService, CurrentUserProvider currentUserProvider) {
		this.hobbyService = hobbyService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내 활동 분야 목록 조회 (기본은 보관된 분야 제외)")
	@GetMapping
	public List<HobbyResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
		return hobbyService.list(currentUserProvider.getCurrentUserId(), includeArchived);
	}

	@Operation(summary = "활동 분야 등록")
	@ApiResponse(responseCode = "409", description = "이미 사용 중인 분야 이름",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<HobbyResponse> create(@Valid @RequestBody HobbyCreateRequest request) {
		HobbyResponse response = hobbyService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "활동 분야 수정 (보낸 칸만 수정, goal을 null로 보내면 비움, archived로 보관·되살리기)")
	@ApiResponse(responseCode = "404", description = "내 활동 분야가 아님",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PatchMapping("/{id}")
	public HobbyResponse update(@PathVariable Long id, @RequestBody HobbyUpdateRequest request) {
		return hobbyService.update(currentUserProvider.getCurrentUserId(), id, request);
	}

	@Operation(summary = "활동 분야 삭제")
	@ApiResponse(responseCode = "409", description = "기록이 있는 분야는 삭제 불가",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		hobbyService.delete(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}
}
