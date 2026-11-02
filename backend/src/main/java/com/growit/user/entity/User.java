package com.growit.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * {@code users} 표에 대응하는 회원 엔티티.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 로그인 이메일. 항상 소문자로 저장하고 가입 시 중복을 검사한다. */
	@Column(nullable = false)
	private String email;

	/** BCrypt로 암호화된 비밀번호. 원문은 어디에도 저장하지 않는다. */
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	/** 화면에 보일 이름, 1~30자. 가입 시 이메일의 {@code @} 앞부분으로 채워진다. */
	@Column(name = "display_name", length = 30)
	private String displayName;

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

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
