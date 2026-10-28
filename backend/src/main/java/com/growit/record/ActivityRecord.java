package com.growit.record;

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
