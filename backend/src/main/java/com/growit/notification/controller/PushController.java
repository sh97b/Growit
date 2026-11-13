package com.growit.notification.controller;

import com.growit.auth.security.CurrentUserProvider;
import com.growit.notification.dto.PushPublicKeyResponse;
import com.growit.notification.dto.PushSubscribeRequest;
import com.growit.notification.service.PushSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.growit.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림", description = "웹 푸시 구독")
@RestController
@RequestMapping("/api/push")
public class PushController {

	private final PushSubscriptionService pushSubscriptionService;
	private final CurrentUserProvider currentUserProvider;

	public PushController(PushSubscriptionService pushSubscriptionService, CurrentUserProvider currentUserProvider) {
		this.pushSubscriptionService = pushSubscriptionService;
		this.currentUserProvider = currentUserProvider;
	}

	@Operation(summary = "VAPID 공개키 조회 (enabled=false면 푸시 기능이 꺼진 상태)")
	@GetMapping("/public-key")
	public PushPublicKeyResponse publicKey() {
		return pushSubscriptionService.publicKey();
	}

	@Operation(summary = "내 기기의 푸시 구독 등록·갱신")
	@PostMapping("/subscriptions")
	public ResponseEntity<Void> subscribe(@RequestBody PushSubscribeRequest request) {
		pushSubscriptionService.subscribe(currentUserProvider.getCurrentUserId(), request);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	/** endpoint를 주면 그 구독만, 비워 두면 내 모든 구독을 지운다. */
	@Operation(summary = "내 푸시 구독 해제 (endpoint 생략하면 내 구독 전부)")
	@DeleteMapping("/subscriptions")
	public ResponseEntity<Void> unsubscribe(@RequestBody(required = false) PushSubscribeRequest request) {
		pushSubscriptionService.unsubscribe(currentUserProvider.getCurrentUserId(),
				request == null ? null : request.endpoint());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@Operation(summary = "내 기기로 테스트 알림 보내기 (하루 5회)")
	@ApiResponse(responseCode = "409", description = "PUSH_DISABLED: 알림 기능이 꺼져 있음 / "
			+ "PUSH_SUBSCRIPTION_INVALID: 구독 형식이 깨짐 / PUSH_SUBSCRIPTION_GONE: 구독이 만료·해지됨",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429", description = "PUSH_LIMIT_EXCEEDED: 오늘 테스트 알림을 다 씀",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "502", description = "PUSH_SEND_FAILED: 알림 전송에 실패함",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping("/test")
	public ResponseEntity<Void> test() {
		pushSubscriptionService.sendTest(currentUserProvider.getCurrentUserId());
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
