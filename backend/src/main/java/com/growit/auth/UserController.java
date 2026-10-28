package com.growit.auth;

import com.growit.auth.dto.MeResponse;
import com.growit.common.ApiException;
import com.growit.common.CurrentUserProvider;
import com.growit.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "로그인", description = "회원가입·로그인")
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	public UserController(UserRepository userRepository, CurrentUserProvider currentUserProvider) {
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내 정보 조회")
	@GetMapping("/me")
	public MeResponse me() {
		User user = userRepository.findById(currentUserProvider.getCurrentUserId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
		return new MeResponse(user.getId(), user.getUsername(), user.getCreatedAt());
	}
}
