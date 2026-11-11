package com.growit.user.service;

import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.repository.AiResultRepository;
import com.growit.auth.oauth.repository.SocialAccountRepository;
import com.growit.auth.security.CurrentUserProvider;
import com.growit.auth.security.JwtTokenProvider;
import com.growit.auth.security.LoginAttemptLimiter;
import com.growit.auth.security.PasswordPolicy;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.notification.repository.NotificationSettingsRepository;
import com.growit.notification.repository.PushSubscriptionRepository;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.dto.ChangePasswordRequest;
import com.growit.user.dto.ChangePasswordResponse;
import com.growit.user.dto.DeleteAccountRequest;
import com.growit.user.dto.MeResponse;
import com.growit.user.dto.UpdateMeRequest;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 30;
	private static final String CURRENT_PASSWORD_WRONG_MESSAGE = "현재 비밀번호가 맞지 않아요";
	private static final String DELETE_PASSWORD_WRONG_MESSAGE = "비밀번호가 맞지 않아요";
	private static final String DELETE_CONFIRMATION_TEXT = "계정 삭제";
	private static final String DELETE_CONFIRMATION_WRONG_MESSAGE = "확인 문구를 정확히 입력해 주세요";

	private final UserRepository userRepository;
	private final CurrentUserProvider currentUserProvider;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final LoginAttemptLimiter loginAttemptLimiter;
	private final JwtTokenProvider jwtTokenProvider;
	private final AiResultRepository aiResultRepository;
	private final AiLogRepository aiLogRepository;
	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final NotificationSettingsRepository notificationSettingsRepository;
	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final SocialAccountRepository socialAccountRepository;

	public UserService(UserRepository userRepository, CurrentUserProvider currentUserProvider,
			PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, LoginAttemptLimiter loginAttemptLimiter,
			JwtTokenProvider jwtTokenProvider, AiResultRepository aiResultRepository, AiLogRepository aiLogRepository,
			ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			NotificationSettingsRepository notificationSettingsRepository,
			PushSubscriptionRepository pushSubscriptionRepository, SocialAccountRepository socialAccountRepository) {
		this.userRepository = userRepository;
		this.currentUserProvider = currentUserProvider;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.loginAttemptLimiter = loginAttemptLimiter;
		this.jwtTokenProvider = jwtTokenProvider;
		this.aiResultRepository = aiResultRepository;
		this.aiLogRepository = aiLogRepository;
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.notificationSettingsRepository = notificationSettingsRepository;
		this.pushSubscriptionRepository = pushSubscriptionRepository;
		this.socialAccountRepository = socialAccountRepository;
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
		if (request.aiEnabled() != null) {
			user.setAiEnabled(request.aiEnabled());
		}
		return toResponse(user);
	}

	/**
	 * 현재 비밀번호가 틀리면 로그인 시도 제한에도 반영한다(비밀번호 변경이 현재 비밀번호 brute-force의
	 * 우회로가 되지 않도록). 새 비밀번호는 정책을 통과해야 하고 현재 비밀번호와 달라야 한다. 성공하면
	 * token_version을 올려 다른 기기의 토큰을 무효화하고, 이 요청을 보낸 세션은 새 토큰으로 이어서 쓴다.
	 */
	@Transactional
	public ChangePasswordResponse changePassword(ChangePasswordRequest request, String clientIp) {
		User user = findCurrentUser();
		if (!user.hasPassword()) {
			throw new ApiException(ErrorCode.SOCIAL_ACCOUNT, "소셜 로그인 계정은 비밀번호를 바꿀 수 없어요");
		}
		loginAttemptLimiter.checkAllowed(user.getEmail(), clientIp);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			loginAttemptLimiter.recordFailure(user.getEmail(), clientIp);
			throw new ApiException(ErrorCode.PASSWORD_MISMATCH, CURRENT_PASSWORD_WRONG_MESSAGE);
		}
		loginAttemptLimiter.recordSuccess(user.getEmail());

		passwordPolicy.validate(request.newPassword());
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "현재 비밀번호와 다른 비밀번호를 써 주세요");
		}

		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		user.incrementTokenVersion();
		String accessToken = jwtTokenProvider.createToken(user.getId(), user.getTokenVersion());
		return new ChangePasswordResponse(accessToken);
	}

	/**
	 * 외래키 제약을 지키려고 ai_results·ai_logs(둘 다 hobbies를 참조하거나 독립적) → records(hobbies를
	 * 참조) → hobbies → social_accounts → users 순으로 지운다. 이미 로그인한 세션에서만 호출할 수 있어(토큰 필요) 로그인
	 * 시도 제한과 달리 따로 횟수를 세지 않는다 - 계정 존재 여부를 숨길 이유도 없고, 토큰을 쥔 사람이 반복
	 * 틀려도 로그인 잠금과 달리 다른 사용자에게 영향이 없다.
	 * 소셜 전용 계정은 비밀번호가 없어 비밀번호 대신 확인 문구("계정 삭제") 입력으로 본인 확인을 한다.
	 */
	@Transactional
	public void deleteAccount(DeleteAccountRequest request) {
		User user = findCurrentUser();
		if (user.hasPassword()) {
			if (!passwordEncoder.matches(request.password() == null ? "" : request.password(), user.getPasswordHash())) {
				throw new ApiException(ErrorCode.PASSWORD_MISMATCH, DELETE_PASSWORD_WRONG_MESSAGE);
			}
		} else if (!DELETE_CONFIRMATION_TEXT.equals(request.confirmation())) {
			throw new ApiException(ErrorCode.PASSWORD_MISMATCH, DELETE_CONFIRMATION_WRONG_MESSAGE);
		}
		Long userId = user.getId();
		pushSubscriptionRepository.deleteByUserId(userId);
		notificationSettingsRepository.deleteByUserId(userId);
		aiResultRepository.deleteByUserId(userId);
		aiLogRepository.deleteByUserId(userId);
		activityRecordRepository.deleteByUserId(userId);
		hobbyRepository.deleteByUserId(userId);
		socialAccountRepository.deleteByUserId(userId);
		userRepository.delete(user);
	}

	private User findCurrentUser() {
		return userRepository.findById(currentUserProvider.getCurrentUserId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
	}

	private MeResponse toResponse(User user) {
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.isAiEnabled(),
				user.hasPassword(), user.getCreatedAt());
	}
}
