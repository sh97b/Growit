package com.growit.notification.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.notification.dto.PushPublicKeyResponse;
import com.growit.notification.dto.PushSubscribeRequest;
import com.growit.notification.entity.PushSubscription;
import com.growit.notification.repository.PushSubscriptionRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {

	private static final int TEST_DAILY_LIMIT = 5;

	private final PushSubscriptionRepository pushSubscriptionRepository;
	private final PushSenderService pushSenderService;

	private final Object testLimitLock = new Object();
	private LocalDate testLimitDay = LocalDate.MIN;
	private final Map<Long, Integer> testSentToday = new HashMap<>();

	public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository,
			PushSenderService pushSenderService) {
		this.pushSubscriptionRepository = pushSubscriptionRepository;
		this.pushSenderService = pushSenderService;
	}

	public PushPublicKeyResponse publicKey() {
		return new PushPublicKeyResponse(pushSenderService.isEnabled(), pushSenderService.publicKey());
	}

	/**
	 * 같은 endpoint가 이미 등록돼 있으면(다른 사용자 포함 — 같은 브라우저에서 계정이 바뀐 경우) 그 구독을
	 * 지금 사용자 것으로 갱신한다. endpoint 자체가 구독 하나를 가리키는 유일한 값이라 그래야 중복이 안 생긴다.
	 */
	@Transactional
	public void subscribe(Long userId, PushSubscribeRequest request) {
		if (request.endpoint() == null || request.endpoint().isBlank() || request.keys() == null
				|| request.keys().p256dh() == null || request.keys().auth() == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "endpoint와 keys(p256dh, auth)가 필요해요");
		}
		if (!PushKeyCodec.isValidP256dh(PushKeyCodec.decode(request.keys().p256dh()))
				|| !PushKeyCodec.isValidAuth(PushKeyCodec.decode(request.keys().auth()))) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "구독 키 형식이 올바르지 않아요");
		}
		PushSubscription existing = pushSubscriptionRepository.findByEndpoint(request.endpoint()).orElse(null);
		if (existing != null && existing.getUserId().equals(userId)) {
			existing.refresh(request.keys().p256dh(), request.keys().auth(), existing.getUserAgent());
			return;
		}
		if (existing != null) {
			// 다른 사용자 것이던 구독(같은 브라우저에서 계정이 바뀐 경우) — 지금 사용자 것으로 다시 만든다.
			pushSubscriptionRepository.delete(existing);
		}
		pushSubscriptionRepository.save(new PushSubscription(userId, request.endpoint(), request.keys().p256dh(),
				request.keys().auth(), null));
	}

	/** endpoint를 주면 그 구독만, 안 주면 내 모든 구독을 지운다. */
	@Transactional
	public void unsubscribe(Long userId, String endpoint) {
		if (endpoint != null && !endpoint.isBlank()) {
			pushSubscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
		} else {
			pushSubscriptionRepository.deleteByUserId(userId);
		}
	}

	/** 하루 5회까지만, 내 기기 전체로 보낸다. 서버가 꺼져 있으면(VAPID 키 없음) AI_FAILED와 같은 결로 조용히 안내. */
	@Transactional
	public void sendTest(Long userId) {
		if (!pushSenderService.isEnabled()) {
			throw new ApiException(ErrorCode.PUSH_DISABLED, "지금은 알림을 보낼 수 없어요.");
		}
		synchronized (testLimitLock) {
			LocalDate today = LocalDate.now();
			if (!today.equals(testLimitDay)) {
				testLimitDay = today;
				testSentToday.clear();
			}
			int sent = testSentToday.getOrDefault(userId, 0);
			if (sent >= TEST_DAILY_LIMIT) {
				throw new ApiException(ErrorCode.PUSH_LIMIT_EXCEEDED, "오늘 보낼 수 있는 테스트 알림 횟수를 다 썼어요.");
			}
			testSentToday.put(userId, sent + 1);
		}

		List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserId(userId);
		String payload = "{\"title\":\"테스트 알림이에요\",\"body\":\"잘 받고 있어요.\"}";
		boolean anyOk = false;
		boolean anyGone = false;
		boolean anyInvalid = false;
		for (PushSubscription subscription : subscriptions) {
			PushSendResult result = pushSenderService.send(subscription, payload);
			switch (result) {
				case OK -> anyOk = true;
				case GONE -> {
					anyGone = true;
					pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
				}
				case INVALID -> {
					anyInvalid = true;
					pushSubscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
				}
				default -> {
					// FAILED·DISABLED는 삭제하지 않고 아래에서 PUSH_SEND_FAILED로 보고한다.
				}
			}
		}
		// 기기 중 하나라도 성공했으면 전체는 성공으로 본다. 그렇지 않으면 가장 먼저 해당하는 이유로 거절한다.
		if (anyOk || subscriptions.isEmpty()) {
			return;
		}
		if (anyInvalid) {
			throw new ApiException(ErrorCode.PUSH_SUBSCRIPTION_INVALID, "알림 연결을 다시 해 주세요.");
		}
		if (anyGone) {
			throw new ApiException(ErrorCode.PUSH_SUBSCRIPTION_GONE, "알림 연결을 다시 해 주세요.");
		}
		throw new ApiException(ErrorCode.PUSH_SEND_FAILED, "알림을 보내지 못했어요. 잠시 후 다시 시도해 주세요");
	}
}
