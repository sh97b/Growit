package com.growit.dot.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.dto.DotCell;
import com.growit.dot.dto.DotGrid;
import java.util.HashSet;
import java.util.Set;

/** 그림 데이터가 어디서 왔든(기본 그림, 이후 사진) 저장 전에 같은 기준으로 확인한다. */
public final class DotGridValidator {

	public static final int MIN_GOAL_DAYS = 14;
	public static final int MAX_GOAL_DAYS = 200;
	public static final int MAX_SIZE = 40;
	public static final int MAX_COLORS = 8;

	private DotGridValidator() {
	}

	public static void validate(int goalDays, DotGrid grid) {
		if (goalDays < MIN_GOAL_DAYS || goalDays > MAX_GOAL_DAYS) {
			throw invalid("목표 일수는 " + MIN_GOAL_DAYS + "일부터 " + MAX_GOAL_DAYS + "일까지예요");
		}
		if (grid.width() < 1 || grid.height() < 1 || grid.width() > MAX_SIZE || grid.height() > MAX_SIZE) {
			throw invalid("그림 크기가 올바르지 않아요");
		}
		if (grid.palette().isEmpty() || grid.palette().size() > MAX_COLORS) {
			throw invalid("그림 색은 1개 이상 " + MAX_COLORS + "개 이하여야 해요");
		}
		for (String color : grid.palette()) {
			if (color == null || !color.matches("#[0-9A-Fa-f]{6}")) {
				throw invalid("그림 색 형식이 올바르지 않아요");
			}
		}
		if (grid.cells().size() != goalDays) {
			throw invalid("그림의 칸 수가 목표 일수와 달라요");
		}
		Set<Integer> seen = new HashSet<>();
		for (DotCell cell : grid.cells()) {
			if (cell.x() < 0 || cell.x() >= grid.width() || cell.y() < 0 || cell.y() >= grid.height()) {
				throw invalid("그림 칸 위치가 범위를 벗어났어요");
			}
			if (cell.color() < 0 || cell.color() >= grid.palette().size()) {
				throw invalid("그림 칸 색 번호가 올바르지 않아요");
			}
			if (!seen.add(cell.y() * MAX_SIZE + cell.x())) {
				throw invalid("같은 자리에 칸이 겹쳐 있어요");
			}
		}
	}

	private static ApiException invalid(String message) {
		return new ApiException(ErrorCode.VALIDATION_ERROR, message);
	}
}
