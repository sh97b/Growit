package com.growit.notification.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.notification.dto.NotificationSettingsResponse;
import com.growit.notification.dto.NotificationSettingsUpdateRequest;
import com.growit.notification.service.NotificationSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "알림 설정")
@RestController
@RequestMapping("/api/notifications/settings")
public class NotificationSettingsController {

	private final NotificationSettingsService notificationSettingsService;
	private final CurrentUserProvider currentUserProvider;

	public NotificationSettingsController(NotificationSettingsService notificationSettingsService,
			CurrentUserProvider currentUserProvider) {
		this.notificationSettingsService = notificationSettingsService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "내 알림 설정 조회 (없으면 기본값으로 만들어 돌려줌)")
	@GetMapping
	public NotificationSettingsResponse get() {
		return notificationSettingsService.get(currentUserProvider.getCurrentUserId());
	}

	@Operation(summary = "내 알림 설정 저장")
	@PutMapping
	public NotificationSettingsResponse update(@RequestBody NotificationSettingsUpdateRequest request) {
		return notificationSettingsService.update(currentUserProvider.getCurrentUserId(), request);
	}
}
