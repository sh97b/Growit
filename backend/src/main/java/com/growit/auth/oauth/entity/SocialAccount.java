package com.growit.auth.oauth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "social_accounts")
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 100)
	private String providerUserId;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected SocialAccount() {
	}

	public SocialAccount(Long userId, SocialProvider provider, String providerUserId) {
		this.userId = userId;
		this.provider = provider;
		this.providerUserId = providerUserId;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public SocialProvider getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}
}
