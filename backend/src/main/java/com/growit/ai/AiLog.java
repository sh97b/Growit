package com.growit.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "ai_logs")
public class AiLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "result_type", nullable = false, length = 20)
	private String resultType;

	@Column(nullable = false, length = 10)
	private String state;

	@Column(name = "response_ms")
	private Integer responseMs;

	@Column(name = "token_count")
	private Integer tokenCount;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected AiLog() {
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getResultType() {
		return resultType;
	}

	public String getState() {
		return state;
	}

	public Integer getResponseMs() {
		return responseMs;
	}

	public Integer getTokenCount() {
		return tokenCount;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
