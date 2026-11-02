package com.growit.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * {@code records} 표에 대응하는 활동 기록 엔티티.
 * <p>클래스 이름이 표 이름(records)과 다른 건 자바 기본 타입 {@code Record}와 이름이 겹치기 때문이다.
 */
@Entity
@Table(name = "records")
public class ActivityRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "hobby_id", nullable = false)
	private Long hobbyId;

	/** 활동한 날짜(사용자가 고른 날, 작성 시각이 아님). 서버 시간(Asia/Seoul) 기준 오늘보다 미래일 수 없다. */
	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	/** 연습 시간(분). 선택 입력이라 null일 수 있다. */
	private Integer minutes;

	/** 막혔던 점이나 나아진 점, 선택 입력. */
	@Column(columnDefinition = "TEXT")
	private String difficulty;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Feeling feeling;

	/** "의미 있는 순간" 표시 여부. 기본 false. */
	@Column(name = "is_milestone", nullable = false, columnDefinition = "BOOLEAN")
	private boolean isMilestone;

	/** AI가 뽑은 주제·어려움 태그(JSON, 모양은 {@code {"topics":[],"difficulties":[]}}). 아직 처리 전이거나 실패했으면 null. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "ai_analysis", columnDefinition = "JSON")
	private String aiAnalysis;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	protected ActivityRecord() {
	}

	public ActivityRecord(Long userId, Long hobbyId, LocalDate recordDate, String content, Integer minutes,
			String difficulty, Feeling feeling, boolean isMilestone) {
		this.userId = userId;
		this.hobbyId = hobbyId;
		this.recordDate = recordDate;
		this.content = content;
		this.minutes = minutes;
		this.difficulty = difficulty;
		this.feeling = feeling;
		this.isMilestone = isMilestone;
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

	public void setHobbyId(Long hobbyId) {
		this.hobbyId = hobbyId;
	}

	public LocalDate getRecordDate() {
		return recordDate;
	}

	public void setRecordDate(LocalDate recordDate) {
		this.recordDate = recordDate;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getMinutes() {
		return minutes;
	}

	public void setMinutes(Integer minutes) {
		this.minutes = minutes;
	}

	public String getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(String difficulty) {
		this.difficulty = difficulty;
	}

	public Feeling getFeeling() {
		return feeling;
	}

	public void setFeeling(Feeling feeling) {
		this.feeling = feeling;
	}

	public boolean isMilestone() {
		return isMilestone;
	}

	public void setMilestone(boolean milestone) {
		isMilestone = milestone;
	}

	public String getAiAnalysis() {
		return aiAnalysis;
	}

	/** 기록 내용이 바뀌면 이전 AI 정리는 더 이상 맞지 않으므로 비운다(다시 만들어질 때까지 null). */
	public void clearAiAnalysis() {
		this.aiAnalysis = null;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
