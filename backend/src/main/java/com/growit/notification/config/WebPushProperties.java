package com.growit.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID 키·연락처가 비어 있으면 푸시 기능 전체가 꺼진 상태로 서버가 정상 시작한다
 * (AiProperties.isEnabled()와 같은 방식 — 셋 다 비밀값이 아니라 "켜짐 여부"를 함께 결정하는 특수 설정이라
 * 기본값 없이 읽어 서버 시작을 막는 보통의 비밀값 규칙과는 다르게 다룬다).
 */
@ConfigurationProperties(prefix = "app.web-push")
public class WebPushProperties {

	private String publicKey = "";
	private String privateKey = "";
	private String subject = "";

	public boolean isEnabled() {
		return !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(String privateKey) {
		this.privateKey = privateKey;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}
}
