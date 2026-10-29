package com.growit.auth;

import com.growit.auth.dto.MeResponse;
import com.growit.auth.dto.UpdateMeRequest;
import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public MeResponse me() {
		User user = findCurrentUser();
		return toResponse(user);
	}

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
