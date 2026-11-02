package com.growit.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * {@code ai_logs} 표에 대응하는 AI 호출 기록(성공·실패·한도초과 이력). 아직 이 표를 쓰는 서비스·컨트롤러는 없다.
 */
@Entity
@Table(name = "ai_logs")
public class AiLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** "ANALYSIS"(기록 정리), "WEEKLY", "COMPARE", "COACH" 중 하나. */
	@Column(name = "result_type", nullable = false, length = 20)
	private String resultType;

	/** "OK"(성공), "FAIL"(실패), "LIMIT"(한도 초과) 중 하나. */
	@Column(nullable = false, length = 10)
	private String state;

	/** 호출에 걸린 시간(밀리초). 호출 자체를 못 했으면(예: 한도 초과) null. */
	@Column(name = "response_ms")
	private Integer responseMs;

	/** 사용한 토큰 수. 응답을 못 받았으면 null. */
	@Column(name = "token_count")
	private Integer tokenCount;

	/** 실패 이유. 성공(OK)이면 null. */
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
