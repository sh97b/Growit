package com.growit.roadmap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roadmap_steps")
public class RoadmapStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "roadmap_id", nullable = false)
	private Long roadmapId;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(nullable = false, length = 40)
	private String title;

	@Column(name = "period_hint", length = 20)
	private String periodHint;

	protected RoadmapStep() {
	}

	public RoadmapStep(Long roadmapId, int sortOrder, String title, String periodHint) {
		this.roadmapId = roadmapId;
		this.sortOrder = sortOrder;
		this.title = title;
		this.periodHint = periodHint;
	}

	public Long getId() {
		return id;
	}

	public Long getRoadmapId() {
		return roadmapId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getPeriodHint() {
		return periodHint;
	}

	public void setPeriodHint(String periodHint) {
		this.periodHint = periodHint;
	}
}
