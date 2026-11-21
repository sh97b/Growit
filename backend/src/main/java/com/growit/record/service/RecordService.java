package com.growit.record.service;

import com.growit.ai.config.AiProperties;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.dot.service.DotGoalService;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.dto.AnalysisFeedbackRequest;
import com.growit.record.dto.AnalysisTagsEditRequest;
import com.growit.record.dto.RecordAnalysisChange;
import com.growit.record.dto.RecordAnalysisResponse;
import com.growit.record.dto.RecordNextAction;
import com.growit.record.dto.RecordRoadmapSuggestion;
import com.growit.record.dto.RecordNextActionResponse;
import com.growit.record.dto.RecordAnalysisSummary;
import com.growit.record.dto.RecordCreateRequest;
import com.growit.record.dto.RecordListResponse;
import com.growit.record.dto.RecordNeighbor;
import com.growit.record.dto.RecordRelatedRecord;
import com.growit.record.dto.RecordResponse;
import com.growit.record.dto.RecordSummaryResponse;
import com.growit.record.dto.RecordUpdateRequest;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.entity.CustomFeeling;
import com.growit.record.event.RecordSavedEvent;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.entity.User;
import com.growit.user.repository.UserRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.persistence.criteria.Predicate;
import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
public class RecordService {

	private static final Logger log = LoggerFactory.getLogger(RecordService.class);

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;
	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final Duration PENDING_TIMEOUT = Duration.ofMinutes(3);
	private static final int TAG_MAX_LENGTH = 12;
	private static final int TAGS_MAX_COUNT = 3;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final UserRepository userRepository;
	private final AiProperties aiProperties;
	private final ApplicationEventPublisher eventPublisher;
	private final RecordAnalysisService recordAnalysisService;
	private final DotGoalService dotGoalService;
	private final CustomFeelingService customFeelingService;

	public RecordService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			UserRepository userRepository, AiProperties aiProperties, ApplicationEventPublisher eventPublisher,
			RecordAnalysisService recordAnalysisService, DotGoalService dotGoalService,
			CustomFeelingService customFeelingService) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.userRepository = userRepository;
		this.aiProperties = aiProperties;
		this.eventPublisher = eventPublisher;
		this.recordAnalysisService = recordAnalysisService;
		this.dotGoalService = dotGoalService;
		this.customFeelingService = customFeelingService;
	}

	@Transactional
	public RecordListResponse list(Long userId, Long hobbyId, LocalDate from, LocalDate to, String q, Integer page,
			Integer size) {
		int pageNumber = page == null || page < 0 ? 0 : page;
		int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		String normalizedQuery = normalizeSearchQuery(q);

		Specification<ActivityRecord> spec = buildSpec(userId, hobbyId, from, to, normalizedQuery);
		PageRequest pageRequest = PageRequest.of(pageNumber, pageSize,
				Sort.by(Sort.Direction.DESC, "recordDate").and(Sort.by(Sort.Direction.DESC, "id")));

		Page<ActivityRecord> result = activityRecordRepository.findAll(spec, pageRequest);
		result.getContent().forEach(this::correctStalePending);
		List<RecordSummaryResponse> items = result.getContent().stream().map(this::toSummaryResponse).toList();
		return new RecordListResponse(items, result.getTotalElements());
	}

	@Transactional
	public RecordResponse get(Long userId, Long id) {
		ActivityRecord record = findOwned(userId, id);
		correctStalePending(record);
		RecordNeighbor previous = findPrevious(userId, record);
		RecordNeighbor next = findNext(userId, record);
		return toDetailResponse(record, previous, next);
	}

	@Transactional
	public RecordResponse create(Long userId, RecordCreateRequest request) {
		Hobby hobby = findOwnedHobby(userId, request.hobbyId());
		assertNotArchived(hobby);
		assertNotFuture(request.recordDate());
		if (request.feeling() != null && request.customFeelingId() != null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "감정은 하나만 고를 수 있어요");
		}
		CustomFeeling customFeeling = request.customFeelingId() == null ? null
				: customFeelingService.findOwned(userId, request.customFeelingId());

		ActivityRecord record = new ActivityRecord(
				userId,
				request.hobbyId(),
				request.recordDate(),
				request.content(),
				request.minutes(),
				request.difficulty(),
				request.feeling(),
				request.isMilestone() != null && request.isMilestone());
		if (customFeeling != null) {
			record.setCustomFeeling(customFeeling.getLabel(), customFeeling.getDirection());
		}
		triggerAnalysis(record, userId);
		activityRecordRepository.save(record);
		publishIfPending(record, userId);
		paintDot(userId, request.recordDate());
		return toDetailResponse(record);
	}

	/**
	 * {@code minutes}·{@code difficulty}·{@code feeling}은 안 보내면 유지, null로 보내면 비운다(JsonNullable로 구분).
	 * 재분석은 글·어려운 점·감정·날짜·분야 중 하나라도 바뀔 때만 트리거한다(minutes·isMilestone만 바뀌면 기존 분석을 그대로 둔다).
	 */
	@Transactional
	public RecordResponse update(Long userId, Long id, RecordUpdateRequest request) {
		ActivityRecord record = findOwned(userId, id);
		LocalDate previousDate = record.getRecordDate();
		boolean analysisRelevantChange = request.hobbyId() != null || request.recordDate() != null
				|| isPresent(request.content()) || isPresent(request.difficulty()) || isPresent(request.feeling())
				|| (request.customFeelingId() != null && request.customFeelingId().isPresent());

		if (request.hobbyId() != null) {
			Hobby hobby = findOwnedHobby(userId, request.hobbyId());
			assertNotArchived(hobby);
			record.setHobbyId(request.hobbyId());
		}
		if (request.recordDate() != null) {
			assertNotFuture(request.recordDate());
			record.setRecordDate(request.recordDate());
		}
		if (isPresent(request.content())) {
			String content = request.content().get();
			if (content == null || content.isBlank()) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "한 일을 입력해 주세요");
			}
			record.setContent(content);
		}
		if (isPresent(request.minutes())) {
			Integer minutes = request.minutes().get();
			if (minutes != null && minutes < 0) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "연습 시간은 0 이상이어야 해요");
			}
			record.setMinutes(minutes);
		}
		if (isPresent(request.difficulty())) {
			record.setDifficulty(request.difficulty().get());
		}
		if (isPresent(request.feeling())) {
			record.setFeeling(request.feeling().get());
			if (request.feeling().get() == null) {
				record.clearCustomFeeling();
			}
		}
		if (request.customFeelingId() != null && request.customFeelingId().isPresent()) {
			Long customFeelingId = request.customFeelingId().get();
			if (customFeelingId == null) {
				record.clearCustomFeeling();
			} else {
				CustomFeeling customFeeling = customFeelingService.findOwned(userId, customFeelingId);
				record.setCustomFeeling(customFeeling.getLabel(), customFeeling.getDirection());
			}
		}
		if (request.isMilestone() != null) {
			record.setMilestone(request.isMilestone());
		}

		if (analysisRelevantChange) {
			triggerAnalysis(record, userId);
			publishIfPending(record, userId);
		}
		if (request.recordDate() != null && !request.recordDate().equals(previousDate)) {
			paintDot(userId, request.recordDate());
		}

		return toDetailResponse(record);
	}

	@Transactional
	public void delete(Long userId, Long id) {
		ActivityRecord record = findOwned(userId, id);
		activityRecordRepository.delete(record);
	}

	/** PENDING이면 그대로 반환하고, 아니면 다시 요청한다. 사용자가 AI를 꺼 두었거나 서버 AI가 꺼져 있으면 409. */
	@Transactional
	public RecordResponse reanalyze(Long userId, Long id) {
		ActivityRecord record = findOwned(userId, id);
		if (record.getAiStatus() == AiStatus.PENDING) {
			return toDetailResponse(record);
		}
		if (!isAiAvailableForUser(userId)) {
			throw new ApiException(ErrorCode.AI_DISABLED, "AI 기능이 꺼져 있어요");
		}
		record.markAnalysisPending(LocalDateTime.now(SERVER_ZONE));
		publishIfPending(record, userId);
		return toDetailResponse(record);
	}

	@Transactional
	public RecordResponse submitFeedback(Long userId, Long id, AnalysisFeedbackRequest request) {
		ActivityRecord record = findOwned(userId, id);
		assertAnalysisReady(record);
		if (request.change() == null && request.tags() == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "변경할 값을 보내 주세요");
		}

		StoredAnalysis stored = recordAnalysisService.readJson(record.getAiAnalysis());
		if (request.change() != null) {
			stored = stored.withChangeFeedback(request.change().name());
		}
		if (request.tags() != null) {
			stored = stored.withTagsFeedback(request.tags().name());
		}
		record.markAnalysisDone(recordAnalysisService.toJson(stored));
		return toDetailResponse(record);
	}

	@Transactional
	public RecordResponse editTags(Long userId, Long id, AnalysisTagsEditRequest request) {
		ActivityRecord record = findOwned(userId, id);
		assertAnalysisReady(record);
		List<String> tags = normalizeTags(request.tags());

		StoredAnalysis stored = recordAnalysisService.readJson(record.getAiAnalysis());
		record.markAnalysisDone(recordAnalysisService.toJson(stored.withEditedTags(tags)));
		return toDetailResponse(record);
	}

	private List<String> normalizeTags(List<String> rawTags) {
		if (rawTags == null) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "태그를 입력해 주세요");
		}
		List<String> normalized = rawTags.stream()
				.map(tag -> tag == null ? "" : tag.trim())
				.filter(tag -> !tag.isBlank())
				.distinct()
				.toList();
		if (normalized.isEmpty() || normalized.size() > TAGS_MAX_COUNT) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "태그는 1~" + TAGS_MAX_COUNT + "개여야 해요");
		}
		for (String tag : normalized) {
			if (tag.length() > TAG_MAX_LENGTH) {
				throw new ApiException(ErrorCode.VALIDATION_ERROR, "태그는 각 " + TAG_MAX_LENGTH + "자 이하여야 해요");
			}
		}
		return normalized;
	}

	/** 가장 최근 기록의 "다음에 해 볼 일". 분석이 끝나지 않았거나 제안이 없으면 null. */
	@Transactional(readOnly = true)
	public RecordNextActionResponse latestNextAction(Long userId) {
		return latestNextAction(userId, null);
	}

	/** hobbyId가 있으면 그 분야의 가장 최근 기록만 본다. 남의 분야거나 없는 분야는 404로 존재를 숨긴다. */
	@Transactional(readOnly = true)
	public RecordNextActionResponse latestNextAction(Long userId, Long hobbyId) {
		ActivityRecord record;
		if (hobbyId == null) {
			record = activityRecordRepository.findFirstByUserIdOrderByRecordDateDescIdDesc(userId).orElse(null);
		} else {
			hobbyRepository.findByIdAndUserId(hobbyId, userId)
					.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
			record = activityRecordRepository
					.findFirstByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(userId, hobbyId).orElse(null);
		}
		return record == null ? null : toNextActionResponse(record);
	}

	/** value는 TRIED(해 봤어요)·HOLD(보류할래요), null이면 상태를 해제한다(아직 안 했어요). 남의 기록이거나 제안이 없으면 404. */
	@Transactional
	public RecordNextActionResponse reactToNextAction(Long userId, Long id, String value) {
		if (value != null && !"TRIED".equals(value) && !"HOLD".equals(value)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "value는 TRIED, HOLD 또는 null이어야 해요");
		}
		ActivityRecord record = findOwned(userId, id);
		RecordNextActionResponse current = toNextActionResponse(record);
		if (current == null) {
			throw new ApiException(ErrorCode.NOT_FOUND, "다음에 해 볼 일을 찾을 수 없어요");
		}
		StoredAnalysis stored = recordAnalysisService.readJson(record.getAiAnalysis());
		record.markAnalysisDone(recordAnalysisService.toJson(stored.withNextActionState(value)));
		return toNextActionResponse(record);
	}

	/** 아직 답하지 않았고 항목이 여전히 내 로드맵의 안 한 항목일 때만 보인다(문장은 로드맵의 저장된 값). */
	private RecordRoadmapSuggestion roadmapSuggestionOf(Long userId, StoredAnalysis stored) {
		StoredRoadmapSuggestion suggestion = stored.roadmapSuggestion();
		if (suggestion == null || suggestion.answer() != null) {
			return null;
		}
		RoadmapContextSource.RoadmapItemRef ref = roadmapContextSource.findPendingItem(userId, suggestion.itemId());
		return ref == null ? null : new RecordRoadmapSuggestion(ref.id(), ref.text(), ref.stepTitle());
	}

	/** value는 CHECKED·DISMISSED. 남의 기록이거나 제안이 없으면 404. 체크 자체는 로드맵 API가 한다. */
	@Transactional
	public void answerRoadmapSuggestion(Long userId, Long id, String value) {
		if (!"CHECKED".equals(value) && !"DISMISSED".equals(value)) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "value는 CHECKED 또는 DISMISSED여야 해요");
		}
		ActivityRecord record = findOwned(userId, id);
		if (record.getAiStatus() != AiStatus.DONE || record.getAiAnalysis() == null) {
			throw new ApiException(ErrorCode.NOT_FOUND, "로드맵 제안을 찾을 수 없어요");
		}
		StoredAnalysis stored = recordAnalysisService.readJson(record.getAiAnalysis());
		if (stored.roadmapSuggestion() == null) {
			throw new ApiException(ErrorCode.NOT_FOUND, "로드맵 제안을 찾을 수 없어요");
		}
		record.markAnalysisDone(recordAnalysisService.toJson(stored.withRoadmapAnswer(value)));
	}

	/** 로드맵 요약(없으면 항상 null이라 기존 동작 그대로). 생성자를 늘리지 않으려고 setter로 받는다. */
	private RoadmapContextSource roadmapContextSource = new RoadmapContextSource() {
		@Override
		public RoadmapContext currentContext(Long userId, Long hobbyId) {
			return null;
		}

		@Override
		public RoadmapItemRef findPendingItem(Long userId, Long itemId) {
			return null;
		}
	};

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	void setRoadmapContextSource(RoadmapContextSource source) {
		this.roadmapContextSource = source;
	}

	/** 근거 기록은 저장된 기록에서 채운다(내 기록이 아니거나 지워졌으면 null). */
	private RecordRelatedRecord evidenceOf(Long userId, StoredNextAction action) {
		if (action.evidenceRecordId() == null) {
			return null;
		}
		return activityRecordRepository.findByIdAndUserId(action.evidenceRecordId(), userId)
				.map(r -> new RecordRelatedRecord(r.getId(), r.getRecordDate(), r.getContent()))
				.orElse(null);
	}

	private RecordNextActionResponse toNextActionResponse(ActivityRecord record) {
		if (record.getAiStatus() != AiStatus.DONE || record.getAiAnalysis() == null) {
			return null;
		}
		StoredNextAction action = recordAnalysisService.readJson(record.getAiAnalysis()).nextAction();
		if (action == null) {
			return null;
		}
		return new RecordNextActionResponse(record.getId(), record.getRecordDate(), action.text(), action.minutes(),
				action.state(), action.reason(), evidenceOf(record.getUserId(), action));
	}

	private void assertAnalysisReady(ActivityRecord record) {
		if (record.getAiStatus() != AiStatus.DONE) {
			throw new ApiException(ErrorCode.ANALYSIS_NOT_READY, "아직 분석이 끝나지 않았어요");
		}
	}

	private boolean isPresent(JsonNullable<?> value) {
		return value != null && value.isPresent();
	}

	/** 서버 AI가 꺼져 있거나 사용자가 ai_enabled를 꺼 두었으면 PENDING 대신 SKIPPED로 남긴다. */
	private void triggerAnalysis(ActivityRecord record, Long userId) {
		if (isAiAvailableForUser(userId)) {
			record.markAnalysisPending(LocalDateTime.now(SERVER_ZONE));
		} else {
			record.markAnalysisSkipped();
		}
	}

	private boolean isAiAvailableForUser(Long userId) {
		if (!aiProperties.isEnabled()) {
			return false;
		}
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없어요"));
		return user.isAiEnabled();
	}

	/**
	 * 도트 그림 칠하기는 기록이 실제로 커밋된 뒤, 별도 트랜잭션으로 돌린다. 실패해도 기록 저장은 그대로 두고 로그만 남기며,
	 * 빠진 날은 {@code GET /api/dots/current}가 다음에 채운다. 삭제는 이미 칠한 칸을 건드리지 않아 따로 처리하지 않는다.
	 */
	private void paintDot(Long userId, LocalDate recordDate) {
		Runnable paint = () -> {
			try {
				dotGoalService.markRecordDate(userId, recordDate);
			} catch (RuntimeException e) {
				log.warn("도트 칠하기에 실패했어요. 다음에 도트 현황을 열 때 채워져요. userId={}", userId, e);
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					paint.run();
				}
			});
		} else {
			paint.run();
		}
	}

	private void publishIfPending(ActivityRecord record, Long userId) {
		if (record.getAiStatus() == AiStatus.PENDING) {
			eventPublisher.publishEvent(new RecordSavedEvent(record.getId(), userId));
		}
	}

	/** 서버 재시작 등으로 멈춘 분석은 3분이 지나면 읽는 시점에 FAILED로 바꿔 그대로 저장한다. */
	private void correctStalePending(ActivityRecord record) {
		if (record.getAiStatus() == AiStatus.PENDING && record.getAiRequestedAt() != null
				&& record.getAiRequestedAt().isBefore(LocalDateTime.now(SERVER_ZONE).minus(PENDING_TIMEOUT))) {
			record.markAnalysisFailed();
		}
	}

	/** 남의 기록은 있는지조차 알려주지 않으려고 403이 아닌 404로 응답한다. */
	private ActivityRecord findOwned(Long userId, Long id) {
		return activityRecordRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
	}

	private Hobby findOwnedHobby(Long userId, Long hobbyId) {
		return hobbyRepository.findByIdAndUserId(hobbyId, userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
	}

	private void assertNotArchived(Hobby hobby) {
		if (hobby.isArchived()) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "보관된 분야에는 새 기록을 쓸 수 없어요");
		}
	}

	/** "오늘"의 기준은 서버 시간대(Asia/Seoul)다. 사용자의 로컬 시간이 아니다. */
	private void assertNotFuture(LocalDate recordDate) {
		if (recordDate.isAfter(LocalDate.now(SERVER_ZONE))) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "미래 날짜로는 기록할 수 없어요");
		}
	}

	private RecordNeighbor findPrevious(Long userId, ActivityRecord record) {
		List<ActivityRecord> result = activityRecordRepository.findPrevious(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), Pageable.ofSize(1));
		return result.isEmpty() ? null : toNeighbor(result.get(0));
	}

	private RecordNeighbor findNext(Long userId, ActivityRecord record) {
		List<ActivityRecord> result = activityRecordRepository.findNext(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), Pageable.ofSize(1));
		return result.isEmpty() ? null : toNeighbor(result.get(0));
	}

	private RecordNeighbor toNeighbor(ActivityRecord record) {
		return new RecordNeighbor(record.getId(), record.getRecordDate());
	}

	private static final int SEARCH_QUERY_MAX_LENGTH = 50;

	/** 공백만 입력하면 필터 없음으로 취급하고, 50자를 넘으면 400으로 거절한다(O1 D). */
	private String normalizeSearchQuery(String q) {
		if (q == null) {
			return null;
		}
		String trimmed = q.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() > SEARCH_QUERY_MAX_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "검색어는 " + SEARCH_QUERY_MAX_LENGTH + "자 이하로 입력해 주세요");
		}
		return trimmed;
	}

	/** %, _, \ 를 글자 그대로 찾도록 이스케이프한다(LIKE 와일드카드 주입 방지). */
	private String escapeLikePattern(String raw) {
		StringBuilder escaped = new StringBuilder();
		for (char c : raw.toCharArray()) {
			if (c == '\\' || c == '%' || c == '_') {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}

	private Specification<ActivityRecord> buildSpec(Long userId, Long hobbyId, LocalDate from, LocalDate to,
			String q) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("userId"), userId));
			if (hobbyId != null) {
				predicates.add(cb.equal(root.get("hobbyId"), hobbyId));
			}
			if (from != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("recordDate"), from));
			}
			if (to != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("recordDate"), to));
			}
			if (q != null) {
				String pattern = "%" + escapeLikePattern(q).toLowerCase() + "%";
				predicates.add(cb.or(
						cb.like(cb.lower(root.get("content")), pattern, '\\'),
						cb.like(cb.lower(root.get("difficulty")), pattern, '\\')));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private RecordResponse toDetailResponse(ActivityRecord record) {
		return toDetailResponse(record, null, null);
	}

	private RecordResponse toDetailResponse(ActivityRecord record, RecordNeighbor previous, RecordNeighbor next) {
		RecordAnalysisResponse analysis = record.getAiStatus() == AiStatus.DONE
				? toAnalysisResponse(record.getUserId(), recordAnalysisService.readJson(record.getAiAnalysis()))
				: null;
		return new RecordResponse(
				record.getId(),
				record.getHobbyId(),
				record.getRecordDate(),
				record.getContent(),
				record.getMinutes(),
				record.getDifficulty(),
				record.getFeeling(),
				record.isMilestone(),
				record.getAiStatus(),
				analysis,
				previous,
				next,
				record.getFeelingLabel(),
				record.getFeelingDirection());
	}

	/** 근거·이어서 볼 기록 목록이 필요해 N+1 조회가 생기므로, 목록 응답에는 쓰지 않는다. */
	private RecordAnalysisResponse toAnalysisResponse(Long userId, StoredAnalysis stored) {
		List<RecordRelatedRecord> related = stored.relatedRecordIds().stream()
				.map(id -> activityRecordRepository.findByIdAndUserId(id, userId).orElse(null))
				.filter(Objects::nonNull)
				.map(r -> new RecordRelatedRecord(r.getId(), r.getRecordDate(), r.getContent()))
				.toList();

		RecordAnalysisChange change = null;
		if (stored.change() != null) {
			ActivityRecord evidence = activityRecordRepository
					.findByIdAndUserId(stored.change().evidenceRecordId(), userId).orElse(null);
			if (evidence != null) {
				change = new RecordAnalysisChange(stored.change().text(), stored.change().support(),
						new RecordRelatedRecord(evidence.getId(), evidence.getRecordDate(), evidence.getContent()));
			}
		}

		RecordNextAction nextAction = stored.nextAction() == null ? null
				: new RecordNextAction(stored.nextAction().text(), stored.nextAction().minutes(),
						stored.nextAction().state(), stored.nextAction().reason(),
						evidenceOf(userId, stored.nextAction()));

		return new RecordAnalysisResponse(stored.title(), stored.tags(), related, change,
				stored.tagsFeedback(), stored.changeFeedback(), nextAction, roadmapSuggestionOf(userId, stored));
	}

	private RecordSummaryResponse toSummaryResponse(ActivityRecord record) {
		RecordAnalysisSummary analysis = null;
		if (record.getAiStatus() == AiStatus.DONE) {
			StoredAnalysis stored = recordAnalysisService.readJson(record.getAiAnalysis());
			analysis = new RecordAnalysisSummary(stored.title(), stored.tags());
		}
		return new RecordSummaryResponse(
				record.getId(),
				record.getHobbyId(),
				record.getRecordDate(),
				record.getContent(),
				record.getMinutes(),
				record.getDifficulty(),
				record.getFeeling(),
				record.isMilestone(),
				record.getAiStatus(),
				analysis,
				record.getFeelingLabel(),
				record.getFeelingDirection());
	}
}
