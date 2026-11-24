package com.growit.roadmap.service;

import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.roadmap.dto.request.RoadmapItemCreateRequest;
import com.growit.roadmap.dto.request.RoadmapItemDoneRequest;
import com.growit.roadmap.dto.request.RoadmapItemUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapStepCreateRequest;
import com.growit.roadmap.dto.request.RoadmapStepUpdateRequest;
import com.growit.roadmap.dto.request.RoadmapUpdateRequest;
import com.growit.roadmap.dto.response.RoadmapItemResponse;
import com.growit.roadmap.dto.response.RoadmapResponse;
import com.growit.roadmap.dto.response.RoadmapStepResponse;
import com.growit.roadmap.entity.Roadmap;
import com.growit.roadmap.entity.RoadmapItem;
import com.growit.roadmap.entity.RoadmapItemOrigin;
import com.growit.roadmap.entity.RoadmapState;
import com.growit.roadmap.entity.RoadmapStep;
import com.growit.roadmap.repository.RoadmapItemRepository;
import com.growit.roadmap.repository.RoadmapRepository;
import com.growit.roadmap.repository.RoadmapStepRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로드맵 조회와 사용자 수정. 남의 로드맵·단계·항목은 존재를 숨기려고 모두 NOT_FOUND(404)로 답하고, 보관된
 * 로드맵은 수정할 수 없다.
 */
@Service
@Transactional(readOnly = true)
public class RoadmapService {

	static final int MAX_STEPS = 8;
	static final int MAX_ITEMS_PER_STEP = 8;
	private static final int TITLE_MAX = 40;
	private static final int PERIOD_HINT_MAX = 20;
	private static final int ITEM_MAX = 60;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

	private final RoadmapRepository roadmapRepository;
	private final RoadmapStepRepository stepRepository;
	private final RoadmapItemRepository itemRepository;
	private final HobbyRepository hobbyRepository;
	private final Clock clock;

	public RoadmapService(RoadmapRepository roadmapRepository, RoadmapStepRepository stepRepository,
			RoadmapItemRepository itemRepository, HobbyRepository hobbyRepository, Clock clock) {
		this.roadmapRepository = roadmapRepository;
		this.stepRepository = stepRepository;
		this.itemRepository = itemRepository;
		this.hobbyRepository = hobbyRepository;
		this.clock = clock;
	}

	/** 진행 중인(ACTIVE) 로드맵. 없으면 null. */
	public RoadmapResponse getActive(Long userId, Long hobbyId) {
		hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		return roadmapRepository.findFirstByHobbyIdAndUserIdAndState(hobbyId, userId, RoadmapState.ACTIVE)
				.map(this::toResponse).orElse(null);
	}

	@Transactional
	public RoadmapResponse updateTarget(Long userId, Long roadmapId, RoadmapUpdateRequest request) {
		Roadmap roadmap = ownedActive(userId, roadmapId);
		if (request.targetDate() != null && request.targetDate().isPresent()) {
			LocalDate date = request.targetDate().get();
			if (date != null && date.isBefore(LocalDate.now(clock.withZone(SERVER_ZONE)))) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "목표일은 오늘 이후 날짜로 정해 주세요");
			}
			roadmap.setTargetDate(date);
		}
		return toResponse(roadmap);
	}

	@Transactional
	public void archive(Long userId, Long roadmapId) {
		ownedActive(userId, roadmapId).archive();
	}

	@Transactional
	public RoadmapResponse addStep(Long userId, Long roadmapId, RoadmapStepCreateRequest request) {
		Roadmap roadmap = ownedActive(userId, roadmapId);
		List<RoadmapStep> steps = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmapId);
		if (steps.size() >= MAX_STEPS) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "단계는 최대 " + MAX_STEPS + "개까지 만들 수 있어요");
		}
		int next = steps.isEmpty() ? 1 : steps.get(steps.size() - 1).getSortOrder() + 1;
		stepRepository.save(new RoadmapStep(roadmapId, next, requireTitle(request.title()),
				blankToNull(request.periodHint(), PERIOD_HINT_MAX)));
		return toResponse(roadmap);
	}

	@Transactional
	public RoadmapResponse updateStep(Long userId, Long stepId, RoadmapStepUpdateRequest request) {
		RoadmapStep step = ownedStep(userId, stepId);
		if (request.title() != null) {
			step.setTitle(requireTitle(request.title()));
		}
		if (request.periodHint() != null && request.periodHint().isPresent()) {
			step.setPeriodHint(blankToNull(request.periodHint().get(), PERIOD_HINT_MAX));
		}
		if (request.position() != null) {
			List<RoadmapStep> steps = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(step.getRoadmapId());
			reorder(steps, step.getId(), request.position(), RoadmapStep::getId, RoadmapStep::setSortOrder);
		} else if (request.move() != null) {
			List<RoadmapStep> steps = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(step.getRoadmapId());
			int index = indexOf(steps, step.getId(), RoadmapStep::getId);
			int other = neighbor(index, steps.size(), request.move());
			if (other != index) {
				swapOrder(steps, index, other);
			}
		}
		return toResponse(roadmapOf(step));
	}

	@Transactional
	public RoadmapResponse deleteStep(Long userId, Long stepId) {
		RoadmapStep step = ownedStep(userId, stepId);
		Roadmap roadmap = roadmapOf(step);
		itemRepository.deleteByStepId(step.getId());
		stepRepository.delete(step);
		stepRepository.flush();
		return toResponse(roadmap);
	}

	@Transactional
	public RoadmapResponse addItem(Long userId, Long stepId, RoadmapItemCreateRequest request) {
		RoadmapStep step = ownedStep(userId, stepId);
		List<RoadmapItem> items = itemRepository.findByStepIdOrderBySortOrderAscIdAsc(stepId);
		if (items.size() >= MAX_ITEMS_PER_STEP) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR,
					"항목은 단계마다 최대 " + MAX_ITEMS_PER_STEP + "개까지 만들 수 있어요");
		}
		int next = items.isEmpty() ? 1 : items.get(items.size() - 1).getSortOrder() + 1;
		itemRepository.save(new RoadmapItem(stepId, next, requireItem(request.text()), RoadmapItemOrigin.USER));
		return toResponse(roadmapOf(step));
	}

	@Transactional
	public RoadmapResponse updateItem(Long userId, Long itemId, RoadmapItemUpdateRequest request) {
		RoadmapItem item = ownedItem(userId, itemId);
		if (request.text() != null) {
			item.setContent(requireItem(request.text()));
		}
		if (request.position() != null) {
			List<RoadmapItem> items = itemRepository.findByStepIdOrderBySortOrderAscIdAsc(item.getStepId());
			reorder(items, item.getId(), request.position(), RoadmapItem::getId, RoadmapItem::setSortOrder);
		} else if (request.move() != null) {
			List<RoadmapItem> items = itemRepository.findByStepIdOrderBySortOrderAscIdAsc(item.getStepId());
			int index = indexOf(items, item.getId(), RoadmapItem::getId);
			int other = neighbor(index, items.size(), request.move());
			if (other != index) {
				RoadmapItem a = items.get(index);
				RoadmapItem b = items.get(other);
				int aOrder = Math.min(a.getSortOrder(), b.getSortOrder());
				int bOrder = Math.max(a.getSortOrder(), b.getSortOrder());
				if (aOrder == bOrder) {
					bOrder = aOrder + 1;
				}
				boolean up = other < index;
				a.setSortOrder(up ? aOrder : bOrder);
				b.setSortOrder(up ? bOrder : aOrder);
			}
		}
		return toResponse(roadmapOf(stepOf(item)));
	}

	@Transactional
	public RoadmapResponse deleteItem(Long userId, Long itemId) {
		RoadmapItem item = ownedItem(userId, itemId);
		Roadmap roadmap = roadmapOf(stepOf(item));
		itemRepository.delete(item);
		itemRepository.flush();
		return toResponse(roadmap);
	}

	@Transactional
	public RoadmapResponse setItemDone(Long userId, Long itemId, RoadmapItemDoneRequest request) {
		RoadmapItem item = ownedItem(userId, itemId);
		boolean done = Boolean.TRUE.equals(request.done());
		if (item.isDone() != done) {
			item.markDone(done, LocalDateTime.now(clock.withZone(SERVER_ZONE)));
		}
		RoadmapStep step = stepOf(item);
		Roadmap roadmap = roadmapOf(step);
		String message = done && item.isDone() ? completedStepMessage(roadmap, step) : null;
		return toResponse(roadmap, message);
	}

	/** 방금 체크한 항목으로 단계의 항목이 모두 끝났으면 차분한 한 줄을 만든다(연출·점수·연속 표현 없음). */
	private String completedStepMessage(Roadmap roadmap, RoadmapStep step) {
		boolean allDone = itemRepository.findByStepIdOrderBySortOrderAscIdAsc(step.getId()).stream()
				.allMatch(RoadmapItem::isDone);
		if (!allDone) {
			return null;
		}
		List<RoadmapStep> steps = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmap.getId());
		int index = indexOf(steps, step.getId(), RoadmapStep::getId);
		if (index + 1 >= steps.size()) {
			return "이 단계를 모두 마쳤어요.";
		}
		String next = steps.get(index + 1).getTitle();
		return "이 단계를 모두 마쳤어요. 다음 단계는 " + next + endingFor(next) + ".";
	}

	/** 받침이 있으면 "이에요", 없으면 "예요". 한글이 아니면 "이에요"로 둔다. */
	private static String endingFor(String word) {
		char last = word.charAt(word.length() - 1);
		if (last >= 0xAC00 && last <= 0xD7A3) {
			return (last - 0xAC00) % 28 == 0 ? "예요" : "이에요";
		}
		return "이에요";
	}

	/** 생성 서비스가 저장한 직후 응답을 만들 때도 쓴다. */
	public RoadmapResponse toResponse(Roadmap roadmap) {
		return toResponse(roadmap, null);
	}

	private RoadmapResponse toResponse(Roadmap roadmap, String completedStepMessage) {
		List<RoadmapStep> steps = stepRepository.findByRoadmapIdOrderBySortOrderAscIdAsc(roadmap.getId());
		Map<Long, List<RoadmapItem>> itemsByStep = steps.isEmpty() ? Map.of()
				: itemRepository.findByStepIdInOrderBySortOrderAscIdAsc(steps.stream().map(RoadmapStep::getId).toList())
						.stream().collect(Collectors.groupingBy(RoadmapItem::getStepId));
		List<RoadmapStepResponse> stepResponses = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			RoadmapStep step = steps.get(i);
			List<RoadmapItem> items = itemsByStep.getOrDefault(step.getId(), List.of());
			List<RoadmapItemResponse> itemResponses = new ArrayList<>();
			for (int j = 0; j < items.size(); j++) {
				RoadmapItem item = items.get(j);
				itemResponses.add(new RoadmapItemResponse(item.getId(), j + 1, item.getContent(), item.isDone(),
						item.getDoneAt(), item.getOrigin().name()));
			}
			stepResponses.add(new RoadmapStepResponse(step.getId(), i + 1, step.getTitle(), step.getPeriodHint(),
					itemResponses));
		}
		return new RoadmapResponse(roadmap.getId(), roadmap.getHobbyId(), roadmap.getState().name(),
				roadmap.getGoalText(),
				roadmap.getTargetDate(), stepResponses, completedStepMessage);
	}

	private Roadmap ownedActive(Long userId, Long roadmapId) {
		Roadmap roadmap = roadmapRepository.findByIdAndUserId(roadmapId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "로드맵을 찾을 수 없어요"));
		if (!roadmap.isActive()) {
			throw new ApiException(ErrorCode.NOT_FOUND, "로드맵을 찾을 수 없어요");
		}
		return roadmap;
	}

	private RoadmapStep ownedStep(Long userId, Long stepId) {
		RoadmapStep step = stepRepository.findById(stepId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "단계를 찾을 수 없어요"));
		ownedActive(userId, step.getRoadmapId());
		return step;
	}

	private RoadmapItem ownedItem(Long userId, Long itemId) {
		RoadmapItem item = itemRepository.findById(itemId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
		RoadmapStep step = stepRepository.findById(item.getStepId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "항목을 찾을 수 없어요"));
		ownedActive(userId, step.getRoadmapId());
		return item;
	}

	private RoadmapStep stepOf(RoadmapItem item) {
		return stepRepository.findById(item.getStepId()).orElseThrow();
	}

	private Roadmap roadmapOf(RoadmapStep step) {
		return roadmapRepository.findById(step.getRoadmapId()).orElseThrow();
	}

	private static String requireTitle(String title) {
		String value = title == null ? "" : title.trim();
		if (value.isEmpty() || value.length() > TITLE_MAX) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "단계 제목은 1~" + TITLE_MAX + "자로 입력해 주세요");
		}
		return value;
	}

	private static String requireItem(String text) {
		String value = text == null ? "" : text.trim();
		if (value.isEmpty() || value.length() > ITEM_MAX) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "항목은 1~" + ITEM_MAX + "자로 입력해 주세요");
		}
		return value;
	}

	private static String blankToNull(String value, int max) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > max) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "기간 힌트는 " + max + "자 이하로 입력해 주세요");
		}
		return trimmed;
	}

	private static <T> int indexOf(List<T> list, Long id, Function<T, Long> idOf) {
		for (int i = 0; i < list.size(); i++) {
			if (idOf.apply(list.get(i)).equals(id)) {
				return i;
			}
		}
		throw new ApiException(ErrorCode.NOT_FOUND, "찾을 수 없어요");
	}

	/** 드래그 앤 드롭: 대상을 목록에서 빼 position(범위 밖이면 끝으로 보정) 자리에 끼우고 1부터 다시 번호를 매긴다. */
	private static <T> void reorder(List<T> list, Long id, int position, java.util.function.Function<T, Long> idOf,
			java.util.function.ObjIntConsumer<T> setOrder) {
		List<T> ordered = new java.util.ArrayList<>(list);
		T target = ordered.remove(indexOf(list, id, idOf));
		ordered.add(Math.max(0, Math.min(position, ordered.size())), target);
		for (int i = 0; i < ordered.size(); i++) {
			setOrder.accept(ordered.get(i), i + 1);
		}
	}

	/** 옮길 수 없는 끝이면 자기 자리를 그대로 돌려준다(오류로 보지 않는다). */
	private static int neighbor(int index, int size, String move) {
		if ("UP".equals(move)) {
			return Math.max(0, index - 1);
		}
		if ("DOWN".equals(move)) {
			return Math.min(size - 1, index + 1);
		}
		throw new ApiException(ErrorCode.VALIDATION_ERROR, "move는 UP 또는 DOWN이어야 해요");
	}

	/** 같은 번호가 겹쳐 있어도 자리가 바뀌도록 낮은 번호·높은 번호를 새로 나눠 갖는다. */
	private void swapOrder(List<RoadmapStep> steps, int index, int other) {
		RoadmapStep a = steps.get(index);
		RoadmapStep b = steps.get(other);
		int low = Math.min(a.getSortOrder(), b.getSortOrder());
		int high = Math.max(a.getSortOrder(), b.getSortOrder());
		if (low == high) {
			high = low + 1;
		}
		boolean up = other < index;
		a.setSortOrder(up ? low : high);
		b.setSortOrder(up ? high : low);
	}
}
