package com.growit.hobby;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

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

	@Column(length = 200)
	private String goal;

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
