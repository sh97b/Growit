package com.growit.dot.dto;

import com.growit.dot.entity.DotSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * mode가 없거나 PRESET이면 presetKey로 기본 그림을, PHOTO이면 브라우저가 사진에서 만든 격자(width, height, palette, cells)를
 * 그대로 받는다. 사진 이미지 자체는 받지 않는다.
 */
public record DotGoalStartRequest(
		@Schema(example = "PRESET") DotSourceType mode,
		@Schema(example = "dumbbell") String presetKey,
		@Schema(example = "내 사진") String title,
		@Schema(example = "30") @NotNull(message = "목표 일수를 입력해 주세요") Integer goalDays,
		Integer width,
		Integer height,
		@Size(max = 8, message = "그림 색은 8개 이하여야 해요") List<String> palette,
		@Size(max = 200, message = "그림 칸은 200개 이하여야 해요") List<@NotNull(message = "그림 칸 정보가 올바르지 않아요") DotPhotoCell> cells) {

	/** 기본 그림으로 시작하는 요청. */
	public DotGoalStartRequest(String presetKey, Integer goalDays) {
		this(null, presetKey, null, goalDays, null, null, null, null);
	}
}
