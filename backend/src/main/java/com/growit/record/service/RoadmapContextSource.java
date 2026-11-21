package com.growit.record.service;

import java.util.List;

/**
 * 기록 분석이 분야의 로드맵을 참고할 수 있게 하는 통로. record 패키지가 roadmap 패키지에 의존하지 않도록
 * 인터페이스는 여기에 두고, 구현은 roadmap 패키지가 맡는다. 구현이 없으면(로드맵 기능이 없는 환경) 기존 동작 그대로다.
 */
public interface RoadmapContextSource {

	/** 분야의 진행 중 로드맵에서 현재 단계(첫 미완료 단계)와 그 단계의 미완료 항목(최대 6개). 없으면 null. */
	RoadmapContext currentContext(Long userId, Long hobbyId);

	/** 내 진행 중 로드맵의 아직 체크하지 않은 항목이면 그 정보를, 남의 것·체크한 것·없는 것이면 null을 준다. */
	RoadmapItemRef findPendingItem(Long userId, Long itemId);

	record RoadmapContext(String stepTitle, List<RoadmapItemRef> items) {
	}

	record RoadmapItemRef(Long id, String text, String stepTitle) {
	}
}
