package com.growit.hobby.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

/**
 * {@code hobbies} 표에 대응하는 활동 분야 엔티티. 한 사용자 안에서 {@code name}은 중복될 수 없다.
 */
@Entity
@Table(name = "hobbies")
public class Hobby {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 50)
	private String name;

	/** 목표 한 줄, 최대 200자. 선택 입력이라 null일 수 있다. */
	@Column(length = 200)
	private String goal;

	/** 보관한 시각. null이면 보관하지 않은(활성) 분야다. {@link #archive}/{@link #unarchive}로만 바꾼다. */
	@Column(name = "archived_at")
	private LocalDateTime archivedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	protected Hobby() {
	}

	public Hobby(Long userId, String name, String goal) {
		this.userId = userId;
		this.name = name;
		this.goal = goal;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getGoal() {
		return goal;
	}

	public void setGoal(String goal) {
		this.goal = goal;
	}

	public LocalDateTime getArchivedAt() {
		return archivedAt;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public void archive(LocalDateTime at) {
		this.archivedAt = at;
	}

	public void unarchive() {
		this.archivedAt = null;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
