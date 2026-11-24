package com.growit.roadmap.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.dto.response.RoadmapSummaryResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapState;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관함: 보관된 로드맵 목록·조회·복원·삭제. 남의 로드맵은 존재를 숨기려고 모두 NOT_FOUND(404)로 답한다.
 * 보관된 로드맵은 AI 입력(RoadmapContextProvider)과 수정 API가 모두 ACTIVE만 보므로 읽기 전용이다.
 */
@Service
@Transactional(readOnly = true)
public class RoadmapArchiveService {

	static final int LIST_LIMIT = 20;

	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemRepository itemRepository;
	private final HobbyRepository hobbyRepository;
	private final RoadmapService roadmapService;

	public RoadmapArchiveService(RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemRepository itemRepository, HobbyRepository hobbyRepository, RoadmapService roadmapService) {
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.itemRepository = itemRepository;
		this.hobbyRepository = hobbyRepository;
		this.roadmapService = roadmapService;
	}

	/** 보관한 지 가장 최근인 것부터 최대 20개. */
	public List<RoadmapSummaryResponse> listArchived(Long userId, Long hobbyId) {
		hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		List<Roadmap> roadmaps = roadmapRepository.findByHobbyIdAndUserIdAndStateOrderByUpdatedAtDescIdDesc(hobbyId,
				userId, RoadmapState.ARCHIVED, PageRequest.of(0, LIST_LIMIT));
		if (roadmaps.isEmpty()) {
			return List.of();
		}
		List<RoadmapStep> steps = stepRepository.findByRoadmapIdIn(roadmaps.stream().map(Roadmap::getId).toList());
		Map<Long, List<RoadmapStep>> stepsByRoadmap = steps.stream()
				.collect(Collectors.groupingBy(RoadmapStep::getRoadmapId));
		Map<Long, Long> roadmapOfStep = steps.stream()
				.collect(Collectors.toMap(RoadmapStep::getId, RoadmapStep::getRoadmapId));
		List<RoadmapItem> items = steps.isEmpty() ? List.of()
				: itemRepository.findByStepIdInOrderBySortOrderAscIdAsc(roadmapOfStep.keySet());
		Map<Long, List<RoadmapItem>> itemsByRoadmap = items.stream()
				.collect(Collectors.groupingBy(item -> roadmapOfStep.get(item.getStepId())));
		return roadmaps.stream().map(roadmap -> {
			List<RoadmapItem> own = itemsByRoadmap.getOrDefault(roadmap.getId(), List.of());
			return new RoadmapSummaryResponse(roadmap.getId(), roadmap.getGoalText(), roadmap.getCreatedAt(),
					roadmap.getUpdatedAt(), stepsByRoadmap.getOrDefault(roadmap.getId(), List.of()).size(), own.size(),
					(int) own.stream().filter(RoadmapItem::isDone).count());
		}).toList();
	}

	/** 진행 중·보관된 로드맵 모두 본인 것이면 조회할 수 있다. */
	public RoadmapResponse get(Long userId, Long roadmapId) {
		return roadmapService.toResponse(owned(userId, roadmapId));
	}

	/**
	 * 보관된 로드맵을 다시 진행 중으로 돌린다. 같은 분야에 진행 중인 로드맵이 있으면 swap이 아닐 때 409이고,
	 * swap이면 한 트랜잭션에서 그것을 보관하고 이것을 복원한다. 분야 행을 잠가 분야당 ACTIVE 1개를 지킨다.
	 */
	@Transactional
	public RoadmapResponse restore(Long userId, Long roadmapId, boolean swap) {
		Roadmap roadmap = owned(userId, roadmapId);
		hobbyRepository.findByIdAndUserIdForUpdate(roadmap.getHobbyId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "로드맵을 찾을 수 없어요"));
		if (roadmap.isActive()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "로드맵을 찾을 수 없어요");
		}
		Roadmap current = roadmapRepository
				.findFirstByHobbyIdAndUserIdAndState(roadmap.getHobbyId(), userId, RoadmapState.ACTIVE).orElse(null);
		if (current != null) {
			if (!swap) {
				throw new ApiException(ErrorCode.ROADMAP_ACTIVE_EXISTS, "이미 진행 중인 로드맵이 있어요");
			}
			current.archive();
			// 유니크 키(분야당 ACTIVE 1개)에 걸리지 않게 보관을 먼저 DB에 반영한다.
			roadmapRepository.saveAndFlush(current);
		}
		roadmap.restore();
		roadmapRepository.saveAndFlush(roadmap);
		return roadmapService.toResponse(roadmap);
	}

	/** 보관된 것만 지울 수 있다(진행 중이면 409). 단계·항목을 함께 지운다. */
	@Transactional
	public void delete(Long userId, Long roadmapId) {
		Roadmap roadmap = owned(userId, roadmapId);
		if (roadmap.isActive()) {
			throw new ApiException(ErrorCode.ROADMAP_NOT_ARCHIVED, "진행 중인 로드맵은 삭제할 수 없어요. 먼저 보관해 주세요");
		}
		itemRepository.deleteByRoadmapId(roadmapId);
		stepRepository.deleteByRoadmapId(roadmapId);
		roadmapRepository.delete(roadmap);
	}

	private Roadmap owned(Long userId, Long roadmapId) {
		return roadmapRepository.findByIdAndUserId(roadmapId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "로드맵을 찾을 수 없어요"));
	}
}
