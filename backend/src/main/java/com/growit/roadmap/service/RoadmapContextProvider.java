package com.growit.roadmap.service;

import com.growit.record.service.RoadmapContextSource;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapState;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 기록 분석이 쓰는 로드맵 요약. 모든 조회에 user_id 조건을 건다. */
@Component
@Transactional(readOnly = true)
public class RoadmapContextProvider implements RoadmapContextSource {

	private static final int MAX_ITEMS = 6;

	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemRepository itemRepository;

	public RoadmapContextProvider(RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemRepository itemRepository) {
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.itemRepository = itemRepository;
	}

	@Override
	public RoadmapContext currentContext(Long userId, Long hobbyId) {
		Optional<Roadmap> roadmap = roadmapRepository.findFirstByHobbyIdAndUserIdAndState(hobbyId, userId,
				RoadmapState.ACTIVE);
		if (roadmap.isEmpty()) {
			return null;
		}
		for (RoadmapStep step : stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmap.get().getId())) {
			List<RoadmapItemRef> pending = itemRepository.findByStepIdOrderBySortOrderAscIdAsc(step.getId()).stream()
					.filter(item -> !item.isDone()).limit(MAX_ITEMS)
					.map(item -> new RoadmapItemRef(item.getId(), item.getContent(), step.getTitle())).toList();
			if (!pending.isEmpty()) {
				return new RoadmapContext(step.getTitle(), pending);
			}
		}
		return null;
	}

	@Override
	public RoadmapItemRef findPendingItem(Long userId, Long itemId) {
		RoadmapItem item = itemRepository.findById(itemId).orElse(null);
		if (item == null || item.isDone()) {
			return null;
		}
		RoadmapStep step = stepRepository.findById(item.getStepId()).orElse(null);
		if (step == null) {
			return null;
		}
		Roadmap roadmap = roadmapRepository.findByIdAndUserId(step.getRoadmapId(), userId).orElse(null);
		if (roadmap == null || !roadmap.isActive()) {
			return null;
		}
		return new RoadmapItemRef(item.getId(), item.getContent(), step.getTitle());
	}
}
