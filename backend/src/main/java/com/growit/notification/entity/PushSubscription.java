package com.growit.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 500)
	private String endpoint;

	@Column(nullable = false, length = 255)
	private String p256dh;

	@Column(nullable = false, length = 255)
	private String auth;

	@Column(name = "user_agent", length = 255)
	private String userAgent;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected PushSubscription() {
	}

	public PushSubscription(Long userId, String endpoint, String p256dh, String auth, String userAgent) {
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
		this.userAgent = userAgent;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public String getP256dh() {
		return p256dh;
	}

	public String getAuth() {
		return auth;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void refresh(String p256dh, String auth, String userAgent) {
		this.p256dh = p256dh;
		this.auth = auth;
		this.userAgent = userAgent;
	}
}
