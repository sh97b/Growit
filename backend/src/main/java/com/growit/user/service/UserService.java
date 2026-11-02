package com.growit.user.service;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 30;

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;

	public UserService(UserRepository userRepository, CurrentUserProvider currentUserProvider) {
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
	}

	public MeResponse me() {
		return toResponse(findCurrentUser());
	}

	/** 이름 앞뒤 공백을 지우고 1~30자인지 검사한 뒤 저장한다(화면에 보일 이름이라 너무 길면 레이아웃이 깨지기 쉬움). */
	@Transactional
	public MeResponse updateMe(UpdateMeRequest request) {
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
