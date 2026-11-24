package com.growit.roadmap.dto.request;

/** swap=true면 같은 분야의 진행 중 로드맵을 보관하고 이 로드맵을 복원한다. 없으면 false로 본다. */
public record RoadmapRestoreRequest(Boolean swap) {

	public boolean isSwap() {
		return Boolean.TRUE.equals(swap);
	}
}
