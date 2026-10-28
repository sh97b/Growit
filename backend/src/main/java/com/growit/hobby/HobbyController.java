package com.growit.hobby;

import com.growit.common.CurrentUserProvider;
import com.growit.hobby.dto.HobbyCreateRequest;
import com.growit.hobby.dto.HobbyResponse;
import com.growit.hobby.dto.HobbyUpdateRequest;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hobbies")
public class HobbyController {

	private final HobbyService hobbyService;
	private final CurrentUserProvider currentUserProvider;

	public HobbyController(HobbyService hobbyService, CurrentUserProvider currentUserProvider) {
		this.hobbyService = hobbyService;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public List<HobbyResponse> list() {
		return hobbyService.list(currentUserProvider.getCurrentUserId());
	}

	@PostMapping
	public ResponseEntity<HobbyResponse> create(@Valid @RequestBody HobbyCreateRequest request) {
		HobbyResponse response = hobbyService.create(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@PatchMapping("/{id}")
	public HobbyResponse update(@PathVariable Long id, @RequestBody HobbyUpdateRequest request) {
		return hobbyService.update(currentUserProvider.getCurrentUserId(), id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		hobbyService.delete(currentUserProvider.getCurrentUserId(), id);
		return ResponseEntity.noContent().build();
	}
}
