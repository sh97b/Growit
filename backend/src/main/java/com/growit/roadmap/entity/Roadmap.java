package com.growit.roadmap.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "roadmaps")
public class Roadmap {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "hobby_id", nullable = false)
	private Long hobbyId;

	/** 만들 때의 분야 목표를 그대로 복사해 둔다(나중에 분야 목표를 고쳐도 이 로드맵의 목표 문장은 유지). */
	@Column(name = "goal_text", nullable = false, length = 200)
	private String goalText;

	@Column(name = "target_date")
	private LocalDate targetDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private RoadmapState state = RoadmapState.ACTIVE;

	@Column(name = "prompt_version", nullable = false)
	private int promptVersion;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	protected Roadmap() {
	}

	public Roadmap(Long userId, Long hobbyId, String goalText, LocalDate targetDate, int promptVersion) {
		this.userId = userId;
		this.hobbyId = hobbyId;
		this.goalText = goalText;
		this.targetDate = targetDate;
		this.promptVersion = promptVersion;
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

	public String getGoalText() {
		return goalText;
	}

	public LocalDate getTargetDate() {
		return targetDate;
	}

	public void setTargetDate(LocalDate targetDate) {
		this.targetDate = targetDate;
	}

	public RoadmapState getState() {
		return state;
	}

	public boolean isActive() {
		return state == RoadmapState.ACTIVE;
	}

	public void archive() {
		this.state = RoadmapState.ARCHIVED;
	}

	public void restore() {
		this.state = RoadmapState.ACTIVE;
	}

	public int getPromptVersion() {
		return promptVersion;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	/** 보관된 로드맵은 수정할 수 없어서 마지막 수정 시각이 곧 보관한 시각이다(별도 칸 없이 쓴다). */
	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
