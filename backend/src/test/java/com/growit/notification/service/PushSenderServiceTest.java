package com.growit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.growit.notification.config.WebPushConfig;
import com.growit.notification.config.WebPushProperties;
import com.growit.notification.entity.PushSubscription;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 가짜 푸시 서비스(JDK 내장 HttpServer, 새 라이브러리 아님)로 실제 암호화 단계까지 전부 거치는지
 * 확인한다. BouncyCastle 등록이 빠지면(WebPushConfig의 static 등록을 지우면) 이 테스트가 깨져야 한다
 * — 2026-11-13 사고(NoClassDefFoundError)의 재발 방지 테스트.
 */
class PushSenderServiceTest {

	// 진짜 EC P-256 VAPID 키쌍(openssl로 생성, 이 테스트 전용이라 비밀값 아님).
	private static final String VAPID_PUBLIC_KEY =
			"BESu_ikHA0x-ghiovFNelqNfQ7mth8BTRpa1mKen0IR70kR2GydaXhapr-k9Hl0KI3HQ4F5C2osJ6WnsN_HqGJk";
	private static final String VAPID_PRIVATE_KEY = "gdTJp7j0i82EpYqzv0cisB7s4polPMJEQ6Zf1pOeBlc";

	// 진짜 EC P-256 곡선 위의 점(openssl로 생성)이어야 한다 — 길이·접두 바이트만 맞춘 무작위 바이트는
	// "Invalid point coordinates"로 암호화 단계 자체가 실패해 이 테스트의 목적(실제 암호화 성공 확인)과
	// 안 맞는다. 구독자 개인키는 필요 없다 — 서버는 구독자 공개키로만 ECDH를 하면 된다.
	private static final String SUBSCRIBER_P256DH =
			"BKYZr8SfATVGUjScIJ-Xu8Bshcxg7NhMP06ZYa78Sd4uJRmiAwp4LtzY5f29M94Mr2YtgomNKuv7JQQjKI2Lw9E";
	private static final String SUBSCRIBER_AUTH = "9NN7EXMOwQz0oOzWaIiEfA";

	private HttpServer fakePushServer;

	/**
	 * 이 테스트는 Spring 컨텍스트 없이(단위 테스트) 돈다 — 운영에서는 {@link WebPushConfig}의 static
	 * 블록이 서버 시작 시 BouncyCastle을 등록하지만, 여기서는 그 설정 클래스를 직접 로딩시켜 같은 일이
	 * 일어나게 한다. 이 줄을 지우면(=등록을 안 하면) 아래 테스트들이 NoSuchProviderException으로 깨진다.
	 */
	@BeforeAll
	static void registerBouncyCastleProvider() {
		new WebPushConfig();
	}

	@AfterEach
	void tearDown() {
		if (fakePushServer != null) {
			fakePushServer.stop(0);
		}
	}

	private PushSenderService senderWithFakeServer(int responseStatus) throws IOException {
		fakePushServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		fakePushServer.createContext("/push", exchange -> {
			exchange.sendResponseHeaders(responseStatus, -1);
			exchange.close();
		});
		fakePushServer.start();

		WebPushProperties properties = new WebPushProperties();
		properties.setPublicKey(VAPID_PUBLIC_KEY);
		properties.setPrivateKey(VAPID_PRIVATE_KEY);
		properties.setSubject("mailto:test@example.com");
		return new PushSenderService(properties);
	}

	private PushSubscription subscriptionTo(int port) {
		return new PushSubscription(1L, "http://127.0.0.1:" + port + "/push", SUBSCRIBER_P256DH, SUBSCRIBER_AUTH, null);
	}

	@Test
	void send_realCryptoAndSuccessfulFakeServer_returnsOk() throws Exception {
		PushSenderService sender = senderWithFakeServer(201);

		PushSendResult result = sender.send(subscriptionTo(fakePushServer.getAddress().getPort()), "{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.OK);
	}

	@Test
	void send_malformedSubscriptionKeys_returnsInvalidWithoutCallingServer() throws Exception {
		PushSenderService sender = senderWithFakeServer(201);
		AtomicInteger calls = new AtomicInteger();
		fakePushServer.createContext("/counted", exchange -> {
			calls.incrementAndGet();
			exchange.sendResponseHeaders(201, -1);
			exchange.close();
		});
		PushSubscription malformed = new PushSubscription(1L,
				"http://127.0.0.1:" + fakePushServer.getAddress().getPort() + "/counted", "not-a-real-key", "short", null);

		PushSendResult result = sender.send(malformed, "{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.INVALID);
		assertThat(calls.get()).isZero();
	}

	@Test
	void send_fakeServerReturns410_returnsGone() throws Exception {
		PushSenderService sender = senderWithFakeServer(410);

		PushSendResult result = sender.send(subscriptionTo(fakePushServer.getAddress().getPort()), "{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.GONE);
	}

	@Test
	void send_fakeServerReturns404_returnsGone() throws Exception {
		PushSenderService sender = senderWithFakeServer(404);

		PushSendResult result = sender.send(subscriptionTo(fakePushServer.getAddress().getPort()), "{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.GONE);
	}

	@Test
	void send_fakeServerReturns500_returnsFailedNotThrown() throws Exception {
		PushSenderService sender = senderWithFakeServer(500);

		PushSendResult result = sender.send(subscriptionTo(fakePushServer.getAddress().getPort()), "{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.FAILED);
	}

	@Test
	void send_disabled_returnsDisabledWithoutNetworkCall() {
		PushSenderService sender = new PushSenderService(new WebPushProperties());

		PushSendResult result = sender.send(
				new PushSubscription(1L, "http://127.0.0.1:1/push", SUBSCRIBER_P256DH, SUBSCRIBER_AUTH, null),
				"{\"title\":\"hi\"}");

		assertThat(result).isEqualTo(PushSendResult.DISABLED);
	}
}
