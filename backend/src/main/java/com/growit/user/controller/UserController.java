package com.growit.user.controller;

import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import com.growit.common.exception.ApiException;
import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 사용자 본인의 정보 조회·수정. 서비스 계층 없이 컨트롤러가 직접 처리한다(로직이 간단해 분리하지 않음).
 */
@Tag(name = "로그인", description = "회원가입·로그인")
@RestController
@RequestMapping("/api/users")
public class UserController {

	private static final int DISPLAY_NAME_MAX_LENGTH = 30;

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	public UserController(UserRepository userRepository, CurrentUserProvider currentUserProvider) {
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
	}

	/** {@code GET /api/users/me} — 토큰의 사용자 id로 본인 정보만 돌려준다. */
	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public MeResponse me() {
		User user = findCurrentUser();
		return toResponse(user);
	}

	/**
	 * {@code PATCH /api/users/me} — 이름 앞뒤 공백을 지우고 1~30자 범위를 검사한 뒤 저장한다.
	 * 공백만 보내거나 30자를 넘으면 VALIDATION_ERROR(400).
	 */
	@Operation(summary = "내 이름 수정 (1~30자, 앞뒤 공백은 지움)")
	@PatchMapping("/me")
	@Transactional
	public MeResponse updateMe(@RequestBody UpdateMeRequest request) {
		User user = findCurrentUser();
		String displayName = request.displayName() == null ? "" : request.displayName().trim();
		if (displayName.isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이름을 입력해 주세요");
		}
		if (displayName.length() > DISPLAY_NAME_MAX_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "이름은 30자 이하로 입력해 주세요");
		}
		user.setDisplayName(displayName);
		return toResponse(user);
	}

	private User findCurrentUser() {
		return userRepository.findById(currentUserProvider.getCurrentUserId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
	}

	private MeResponse toResponse(User user) {
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
	}
}
