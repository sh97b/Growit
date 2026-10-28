package com.growit.record;

import com.growit.common.CurrentUserProvider;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordUpdateRequest;
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

@RestController
@RequestMapping("/api/records")
public class RecordController {

	private final RecordService recordService;
	private final CurrentUserProvider currentUserProvider;

	public RecordController(RecordService recordService, CurrentUserProvider currentUserProvider) {
		this.recordService = recordService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public RecordListResponse list(
			@RequestParam(required = false) Long hobbyId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return recordService.list(currentUserProvider.getCurrentUserId(), hobbyId, from, to, page, size);
	}

	@GetMapping("/{id}")
	public RecordResponse get(@PathVariable Long id) {
		return recordService.get(currentUserProvider.getCurrentUserId(), id);
	}

	@PostMapping
	public ResponseEntity<RecordResponse> create(@Valid @RequestBody RecordCreateRequest request) {
		RecordResponse response = recordService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PatchMapping("/{id}")
	public RecordResponse update(@PathVariable Long id, @RequestBody RecordUpdateRequest request) {
		return recordService.update(currentUserProvider.getCurrentUserId(), id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		recordService.delete(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}
}
