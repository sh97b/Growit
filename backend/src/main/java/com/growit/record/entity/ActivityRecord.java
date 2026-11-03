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

	/** 사용자가 고른 활동 날짜. 작성 시각(createdAt)과 다르다. */
	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	private Integer minutes;

	@Column(columnDefinition = "TEXT")
	private String difficulty;

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private Feeling feeling;

	@Column(name = "is_milestone", nullable = false, columnDefinition = "BOOLEAN")
	private boolean isMilestone;

	/** AI가 뽑은 제목·태그·관련 기록·변화 설명(JSON). DONE 상태가 아니면 null. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "ai_analysis", columnDefinition = "JSON")
	private String aiAnalysis;

	@Enumerated(EnumType.STRING)
	@Column(name = "ai_status", nullable = false, length = 10)
	private AiStatus aiStatus = AiStatus.NONE;

	/** PENDING으로 바뀐 시각. 3분이 지나도 그대로면 멈춘 작업으로 보고 읽을 때 FAILED로 바꾼다. */
	@Column(name = "ai_requested_at")
	private LocalDateTime aiRequestedAt;

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

	public AiStatus getAiStatus() {
		return aiStatus;
	}

	public LocalDateTime getAiRequestedAt() {
		return aiRequestedAt;
	}

	public void markAnalysisPending(LocalDateTime requestedAt) {
		this.aiStatus = AiStatus.PENDING;
		this.aiRequestedAt = requestedAt;
		this.aiAnalysis = null;
	}

	public void markAnalysisSkipped() {
		this.aiStatus = AiStatus.SKIPPED;
		this.aiRequestedAt = null;
		this.aiAnalysis = null;
	}

	public void markAnalysisDone(String analysisJson) {
		this.aiStatus = AiStatus.DONE;
		this.aiAnalysis = analysisJson;
	}

	public void markAnalysisFailed() {
		this.aiStatus = AiStatus.FAILED;
	}

	/** DELETE /api/ai/data 전용: SKIPPED는 그대로 두고, 그 외에는 NONE으로 되돌리며 분석 결과는 항상 비운다. */
	public void resetAnalysisForDataDeletion() {
		if (this.aiStatus != AiStatus.SKIPPED) {
			this.aiStatus = AiStatus.NONE;
		}
		this.aiAnalysis = null;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
