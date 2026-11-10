package com.growit.notification.service;

import com.growit.notification.config.WebPushProperties;
import com.growit.notification.entity.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.springframework.stereotype.Service;

/**
 * 실제 전송은 nl.martijndwars.webpush에 맡긴다. VAPID 키가 비어 있으면(WebPushProperties.isEnabled()
 * false) 아무 것도 보내지 않고 DISABLED를 돌려준다 — 서버는 그래도 정상 시작한다.
 */
@Service
public class PushSenderService {

	private final WebPushProperties properties;
	private volatile nl.martijndwars.webpush.PushService delegate;

	public PushSenderService(WebPushProperties properties) {
		this.properties = properties;
	}

	public boolean isEnabled() {
		return properties.isEnabled();
	}

	public String publicKey() {
		return properties.getPublicKey();
	}

	public PushSendResult send(PushSubscription subscription, String payload) {
		if (!isEnabled()) {
			return PushSendResult.DISABLED;
		}
		try {
			Subscription sub = new Subscription(subscription.getEndpoint(),
					new Subscription.Keys(subscription.getP256dh(), subscription.getAuth()));
			Notification notification = new Notification(sub, payload);
			HttpResponse response = delegate().send(notification);
			int status = response.getStatusLine().getStatusCode();
			if (status == 404 || status == 410) {
				return PushSendResult.GONE;
			}
			return status >= 200 && status < 300 ? PushSendResult.OK : PushSendResult.FAILED;
		} catch (Exception e) {
			return PushSendResult.FAILED;
		}
	}

	private nl.martijndwars.webpush.PushService delegate() throws Exception {
		nl.martijndwars.webpush.PushService instance = delegate;
		if (instance == null) {
			synchronized (this) {
				instance = delegate;
				if (instance == null) {
					instance = new nl.martijndwars.webpush.PushService(properties.getPublicKey(),
							properties.getPrivateKey(), properties.getSubject());
					delegate = instance;
				}
			}
		}
		return instance;
	}
}
