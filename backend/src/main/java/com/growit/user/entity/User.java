package com.growit.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "display_name", length = 30)
	private String displayName;

	@Column(name = "ai_enabled", nullable = false)
	private boolean aiEnabled = true;

	/** 비밀번호를 바꿀 때마다 올려서, 그 전에 발급된 토큰(다른 기기 포함)을 전부 무효로 만든다. */
	@Column(name = "token_version", nullable = false)
	private int tokenVersion = 0;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected User() {
	}

	public User(String email, String passwordHash, String displayName) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public boolean isAiEnabled() {
		return aiEnabled;
	}

	public void setAiEnabled(boolean aiEnabled) {
		this.aiEnabled = aiEnabled;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public int getTokenVersion() {
		return tokenVersion;
	}

	public void incrementTokenVersion() {
		this.tokenVersion++;
	}
}
