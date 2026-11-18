package com.growit.dot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** 한 번 칠해진 날. records와 연결하지 않는다 — 기록을 지워도 칸은 남아야 해서다. */
@Entity
@Table(name = "dot_progress")
@IdClass(DotProgressId.class)
public class DotProgress {

	@Id
	@Column(name = "goal_id", nullable = false)
	private Long goalId;

	@Id
	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	protected DotProgress() {
	}

	public DotProgress(Long goalId, LocalDate recordDate) {
		this.goalId = goalId;
		this.recordDate = recordDate;
	}

	public Long getGoalId() {
		return goalId;
	}

	public LocalDate getRecordDate() {
		return recordDate;
	}
}
