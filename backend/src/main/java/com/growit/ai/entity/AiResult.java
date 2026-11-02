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

/**
 * {@code ai_results} 표에 대응하는 AI 결과 캐시. 아직 이 표를 쓰는 서비스·컨트롤러는 없다(엔티티만 먼저 만들어 둠).
 * <p>같은 입력(분야·기간·근거 기록)에 대해 다시 호출하지 않도록 {@code cacheKey}로 재사용 여부를 판단할 예정이다.
 */
@Entity
@Table(name = "ai_results")
public class AiResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 결과가 특정 분야에 대한 것이면 그 분야 번호, 전체 대상이면 null. */
	@Column(name = "hobby_id")
	private Long hobbyId;

	/** "WEEKLY"(주간 한 줄 요약), "COMPARE"(변화 비교), "COACH"(AI 회고) 중 하나. */
	@Column(name = "result_type", nullable = false, length = 20)
	private String resultType;

	/** 분석 시작일. COMPARE는 비교 대상이 되는 과거 날짜. */
	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	/** 분석 끝일. COMPARE는 오늘. */
	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	/** AI 응답 본문(JSON). 모양은 {@code resultType}마다 다르다(API 명세 참고). */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "JSON")
	private String result;

	/** 근거로 쓴 기록 번호 목록(JSON 배열). 화면은 이 번호로 실제 기록을 다시 조회해 근거 칩을 보여준다. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "record_ids", nullable = false, columnDefinition = "JSON")
	private String recordIds;

	/** 같은 요청인지 판단하는 재사용 열쇠(SHA-256, 64자리 16진수). 사용자별로 유일해야 한다. */
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
