package com.growit.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "roadmap_items")
public class RoadmapItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "step_id", nullable = false)
	private Long stepId;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false, length = 60)
	private String content;

	@Column(nullable = false)
	private boolean done;

	@Column(name = "done_at", columnDefinition = "DATETIME")
	private LocalDateTime doneAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 4)
	private RoadmapItemOrigin origin;

	protected RoadmapItem() {
	}

	public RoadmapItem(Long stepId, int sortOrder, String content, RoadmapItemOrigin origin) {
		this.stepId = stepId;
		this.sortOrder = sortOrder;
		this.content = content;
		this.origin = origin;
	}

	public Long getId() {
		return id;
	}

	public Long getStepId() {
		return stepId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public boolean isDone() {
		return done;
	}

	public LocalDateTime getDoneAt() {
		return doneAt;
	}

	public void markDone(boolean value, LocalDateTime at) {
		this.done = value;
		this.doneAt = value ? at : null;
	}

	public RoadmapItemOrigin getOrigin() {
		return origin;
	}
}
