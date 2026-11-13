package com.growit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.notification.config.WebPushProperties;
import com.growit.notification.dto.PushSubscribeRequest;
import com.growit.notification.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class PushSubscriptionServiceTest {

	// 2026-11-13 사고 이후 PushSubscriptionService.subscribe가 형식(길이·접두 바이트)을 검증하므로
	// 테스트 픽스처도 진짜 EC P-256 공개키(65바이트, 0x04 시작)·16바이트 auth 모양을 갖춰야 한다.
	private static final String P256DH_A = "BKAjjhW0MhkfWq6HM-3Q-ngvw9NlZNi69XsoJJCEanbYegIpfLGi2nej-JcZjeeqqe8dQwt9wqdvRa5t4xWHcuk";
	private static final String P256DH_B = "BP_UTqVh88D3oyJDrmZEd5ZWVKZ0b2kHyeqEknVsNF9O9yqs07Fhkpp0S3isTQekcn2R5Es0B6ghEdyWD5ggVIQ";
	private static final String AUTH = "9NN7EXMOwQz0oOzWaIiEfA";

	@Autowired
	private PushSubscriptionRepository pushSubscriptionRepository;

	private PushSubscriptionService service(boolean pushEnabled) {
		WebPushProperties properties = new WebPushProperties();
		if (pushEnabled) {
			properties.setPublicKey("pub");
			properties.setPrivateKey("priv");
			properties.setSubject("mailto:test@example.com");
		}
		PushSenderService senderService = new PushSenderService(properties);
		return new PushSubscriptionService(pushSubscriptionRepository, senderService);
	}

	@Test
	void publicKey_disabledByDefault_reportsDisabled() {
		var response = service(false).publicKey();

		assertThat(response.enabled()).isFalse();
	}

	@Test
	void publicKey_enabled_reportsPublicKey() {
		var response = service(true).publicKey();

		assertThat(response.enabled()).isTrue();
		assertThat(response.publicKey()).isEqualTo("pub");
	}

	@Test
	void subscribe_newEndpoint_savesSubscription() {
		service(false).subscribe(1L, new PushSubscribeRequest("https://push.example.com/a",
				new PushSubscribeRequest.Keys(P256DH_A, AUTH)));

		assertThat(pushSubscriptionRepository.findByUserId(1L)).hasSize(1);
	}

	@Test
	void subscribe_sameEndpointDifferentUser_reassignsToNewUser() {
		PushSubscriptionService service = service(false);
		service.subscribe(1L, new PushSubscribeRequest("https://push.example.com/a",
				new PushSubscribeRequest.Keys(P256DH_A, AUTH)));

		service.subscribe(2L, new PushSubscribeRequest("https://push.example.com/a",
				new PushSubscribeRequest.Keys(P256DH_B, AUTH)));

		assertThat(pushSubscriptionRepository.findByUserId(1L)).isEmpty();
		var subscription = pushSubscriptionRepository.findByEndpoint("https://push.example.com/a").orElseThrow();
		assertThat(subscription.getUserId()).isEqualTo(2L);
		assertThat(subscription.getP256dh()).isEqualTo(P256DH_B);
	}

	@Test
	void subscribe_missingKeys_throwsValidationError() {
		assertThatThrownBy(() -> service(false).subscribe(1L, new PushSubscribeRequest("https://push.example.com/a", null)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void subscribe_malformedKeys_throwsValidationError() {
		assertThatThrownBy(() -> service(false).subscribe(1L, new PushSubscribeRequest("https://push.example.com/a",
				new PushSubscribeRequest.Keys("not-a-real-key", "auth"))))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
	}

	@Test
	void unsubscribe_withEndpoint_deletesOnlyThatOne() {
		PushSubscriptionService service = service(false);
		service.subscribe(1L, new PushSubscribeRequest("https://push.example.com/a", new PushSubscribeRequest.Keys(P256DH_A, AUTH)));
		service.subscribe(1L, new PushSubscribeRequest("https://push.example.com/b", new PushSubscribeRequest.Keys(P256DH_A, AUTH)));

		service.unsubscribe(1L, "https://push.example.com/a");

		assertThat(pushSubscriptionRepository.findByUserId(1L)).hasSize(1);
	}

	@Test
	void unsubscribe_withoutEndpoint_deletesAllMySubscriptions() {
		PushSubscriptionService service = service(false);
		service.subscribe(1L, new PushSubscribeRequest("https://push.example.com/a", new PushSubscribeRequest.Keys(P256DH_A, AUTH)));
		service.subscribe(1L, new PushSubscribeRequest("https://push.example.com/b", new PushSubscribeRequest.Keys(P256DH_A, AUTH)));

		service.unsubscribe(1L, null);

		assertThat(pushSubscriptionRepository.findByUserId(1L)).isEmpty();
	}

	@Test
	void sendTest_pushDisabled_throwsPushDisabled() {
		assertThatThrownBy(() -> service(false).sendTest(1L))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PUSH_DISABLED));
	}
}
