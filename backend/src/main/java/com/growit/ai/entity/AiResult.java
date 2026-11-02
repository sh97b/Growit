package com.growit.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_results")
public class AiResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "hobby_id")
	private Long hobbyId;

	@Column(name = "result_type", nullable = false, length = 20)
	private String resultType;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "JSON")
	private String result;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "record_ids", nullable = false, columnDefinition = "JSON")
	private String recordIds;

	/** 같은 요청인지 판단하는 재사용 열쇠. 같으면 AI를 다시 부르지 않고 이 결과를 그대로 돌려준다. */
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "cache_key", nullable = false, length = 64)
	private String cacheKey;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected AiResult() {
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getHobbyId() {
		return hobbyId;
	}

	public String getResultType() {
		return resultType;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public LocalDate getEndDate() {
		return endDate;
	}

	public String getResult() {
		return result;
	}

	public String getRecordIds() {
		return recordIds;
	}

	public String getCacheKey() {
		return cacheKey;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
