package com.growit.dot.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class DotProgressId implements Serializable {

	private Long goalId;
	private LocalDate recordDate;

	protected DotProgressId() {
	}

	public DotProgressId(Long goalId, LocalDate recordDate) {
		this.goalId = goalId;
		this.recordDate = recordDate;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DotProgressId that)) {
			return false;
		}
		return Objects.equals(goalId, that.goalId) && Objects.equals(recordDate, that.recordDate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(goalId, recordDate);
	}
}
