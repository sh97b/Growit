package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.AiCoachHistoryResponse;
import com.growit.ai.dto.AiCoachResponse;
import com.growit.ai.dto.CoachActionResponse;
import com.growit.ai.dto.CoachEvidence;
import com.growit.ai.dto.CoachRequest;
import com.growit.ai.dto.CoachTextItemResponse;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.notification.service.NotificationSchedulerService;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import com.growit.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoachService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final int REQUIRED_RECORD_COUNT = 5;
	private static final int MAX_INPUT_RECORDS = 30;
	private static final int CONTENT_MAX = 300;
	private static final int DIFFICULTY_MAX = 200;
	private static final int COMEBACK_THRESHOLD_DAYS = 7;
	private static final int PERIOD_SUMMARY_MAX = 200;
	private static final int TEXT_ITEM_MAX = 200;
	private static final int ACTION_TITLE_MAX = 40;
	private static final int ACTION_REASON_MAX = 200;
	private static final String PROMPT_RESOURCE_NAME = "ai-coach-system.txt";
	/** prompts/ai-coach-system.txt를 바꿀 때마다 올린다. */
	static final int PROMPT_VERSION = 2;
	private static final List<String> FORBIDDEN_WORDS =
			List.of("실력", "늘었", "향상", "잘했", "훌륭", "대단", "점수", "순위", "반드시", "오늘 안에");
	private static final String SIMPLIFY_PROMPT_RESOURCE_NAME = "ai-simplify-system.txt";
	/** prompts/ai-simplify-system.txt를 바꿀 때마다 올린다. */
	static final int SIMPLIFY_PROMPT_VERSION = 1;
	private static final int MAX_SIMPLIFY_EVIDENCE = 5;
	private static final int SIMPLIFY_MIN_MINUTES = 1;
	private static final int SIMPLIFY_MAX_MINUTES = 3;

	PromptResourceLoader simplifyPromptLoader = new PromptResourceLoader(SIMPLIFY_PROMPT_RESOURCE_NAME);

	private static final int AUTO_PERIOD_DAYS = 14;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiResultRepository aiResultRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final ObjectMapper objectMapper;
	private final UserRepository userRepository;
	private final NotificationSchedulerService notificationSchedulerService;

	PromptResourceLoader promptLoader = new PromptResourceLoader(PROMPT_RESOURCE_NAME);

	public CoachService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			AiResultRepository aiResultRepository, AiCallService aiCallService,
			AiResponseValidator aiResponseValidator, ObjectMapper objectMapper, UserRepository userRepository,
			NotificationSchedulerService notificationSchedulerService) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.aiResultRepository = aiResultRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.objectMapper = objectMapper;
		this.userRepository = userRepository;
		this.notificationSchedulerService = notificationSchedulerService;
	}

	@Transactional
	public AiCoachResponse coach(Long userId, CoachRequest request) {
		Hobby hobby = null;
		if (request.hobbyId() != null) {
			hobby = hobbyRepository.findByIdAndUserId(request.hobbyId(), userId)
					.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		}

		LocalDate today = LocalDate.now(SERVER_ZONE);
		LocalDate start = periodStart(request.period(), today);

		List<ActivityRecord> periodRecords = hobby != null
				? activityRecordRepository.findByUserIdAndHobbyIdAndRecordDateBetween(userId, hobby.getId(), start, today)
				: activityRecordRepository.findByUserIdAndRecordDateBetween(userId, start, today);

		if (periodRecords.size() < REQUIRED_RECORD_COUNT) {
			return AiCoachResponse.locked(REQUIRED_RECORD_COUNT, periodRecords.size());
		}

		List<ActivityRecord> inputRecords = periodRecords.stream()
				.sorted(Comparator.comparing(ActivityRecord::getRecordDate).reversed()
						.thenComparing(Comparator.comparing(ActivityRecord::getId).reversed()))
				.limit(MAX_INPUT_RECORDS)
				.toList();

		LocalDate lastRecordDate = hobby != null
				? activityRecordRepository.findMaxRecordDateByUserIdAndHobbyId(userId, hobby.getId())
				: activityRecordRepository.findMaxRecordDateByUserId(userId);
		long daysSinceLastRecord = ChronoUnit.DAYS.between(lastRecordDate, today);
		String mode = daysSinceLastRecord >= COMEBACK_THRESHOLD_DAYS ? "COMEBACK" : "NORMAL";

		Map<Long, ActivityRecord> inputById = new LinkedHashMap<>();
		inputRecords.forEach(r -> inputById.put(r.getId(), r));

		String cacheKey = CacheKeys.sha256(PROMPT_VERSION + "|COACH|" + (hobby == null ? "ALL" : hobby.getId())
				+ "|" + start + "|" + today + "|"
				+ inputRecords.stream().sorted(Comparator.comparing(ActivityRecord::getId))
						.map(r -> r.getId() + ":" + r.getUpdatedAt()).reduce("", (a, b) -> a + "," + b));

		AiResult existing = aiResultRepository.findByUserIdAndCacheKey(userId, cacheKey).orElse(null);
		if (existing != null) {
			return toResponse(existing, true, userId);
		}

		String systemPrompt;
		try {
			systemPrompt = promptLoader.load();
		} catch (java.io.IOException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		List<ComparePayloadHobby> hobbies = hobby != null
				? List.of(new ComparePayloadHobby(hobby.getName(), hobby.getGoal()))
				: distinctHobbies(userId, inputRecords);

		List<ComparePayloadRecord> payloadRecords = inputRecords.stream()
				.map(r -> new ComparePayloadRecord(r.getId(), r.getRecordDate().toString(),
						truncate(r.getContent(), CONTENT_MAX), truncate(r.getDifficulty(), DIFFICULTY_MAX)))
				.toList();

		CoachPayload payload = new CoachPayload(mode, hobbies, (int) daysSinceLastRecord, payloadRecords);

		AiCallResult result = aiCallService.call(userId, AiResultType.COACH, systemPrompt, payload);
		StoredCoachResult stored = validate(result.content(), mode, inputById.keySet(), inputRecords.size());

		AiResult saved = new AiResult(userId, hobby == null ? null : hobby.getId(), AiResultType.COACH.name(), start,
				today, toJson(stored), toJson(inputRecords.stream().map(ActivityRecord::getId).toList()), cacheKey,
				PROMPT_VERSION, result.model());
		aiResultRepository.save(saved);

		return toResponse(saved, false, userId, stored, inputById);
	}

	/**
	 * 그 주에 처음 접속할 때만(홈이 하루 한 번 호출) 최근 14일·전체 분야로 자동 생성한다. AI 꺼짐·기록 5개
	 * 미만·이미 이번 주 COACH 결과가 있음·한도 소진은 모두 오류가 아니라 "지금은 만들 게 없다"는 뜻이라
	 * locked 모양으로 조용히 돌려준다(홈은 이 응답 자체를 보여주지 않고, 그다음 GET .../latest로 읽는다).
	 */
	@Transactional
	public AiCoachResponse generateAuto(Long userId) {
		var user = userRepository.findById(userId).orElse(null);
		if (user == null || !user.isAiEnabled()) {
			return AiCoachResponse.locked(REQUIRED_RECORD_COUNT, 0);
		}

		LocalDate today = LocalDate.now(SERVER_ZONE);
		LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
		boolean hasResultThisWeek = aiResultRepository.existsByUserIdAndResultTypeAndCreatedAtGreaterThanEqual(userId,
				AiResultType.COACH.name(), weekStart.atStartOfDay());
		if (hasResultThisWeek) {
			AiCoachResponse existing = latest(userId);
			return existing != null ? existing : AiCoachResponse.locked(REQUIRED_RECORD_COUNT, 0);
		}

		LocalDate start = today.minusDays(AUTO_PERIOD_DAYS - 1L);
		List<ActivityRecord> periodRecords = activityRecordRepository.findByUserIdAndRecordDateBetween(userId, start,
				today);
		if (periodRecords.size() < REQUIRED_RECORD_COUNT) {
			return AiCoachResponse.locked(REQUIRED_RECORD_COUNT, periodRecords.size());
		}

		List<ActivityRecord> inputRecords = periodRecords.stream()
				.sorted(Comparator.comparing(ActivityRecord::getRecordDate).reversed()
						.thenComparing(Comparator.comparing(ActivityRecord::getId).reversed()))
				.limit(MAX_INPUT_RECORDS)
				.toList();

		LocalDate lastRecordDate = activityRecordRepository.findMaxRecordDateByUserId(userId);
		long daysSinceLastRecord = ChronoUnit.DAYS.between(lastRecordDate, today);
		String mode = daysSinceLastRecord >= COMEBACK_THRESHOLD_DAYS ? "COMEBACK" : "NORMAL";

		Map<Long, ActivityRecord> inputById = new LinkedHashMap<>();
		inputRecords.forEach(r -> inputById.put(r.getId(), r));

		String cacheKey = CacheKeys.sha256(PROMPT_VERSION + "|AUTO_COACH|ALL|" + start + "|" + today + "|"
				+ inputRecords.stream().sorted(Comparator.comparing(ActivityRecord::getId))
						.map(r -> r.getId() + ":" + r.getUpdatedAt()).reduce("", (a, b) -> a + "," + b));

		AiResult existingCache = aiResultRepository.findByUserIdAndCacheKey(userId, cacheKey).orElse(null);
		if (existingCache != null) {
			return toResponse(existingCache, true, userId);
		}

		try {
			String systemPrompt = promptLoader.load();
			List<ComparePayloadHobby> hobbies = distinctHobbies(userId, inputRecords);
			List<ComparePayloadRecord> payloadRecords = inputRecords.stream()
					.map(r -> new ComparePayloadRecord(r.getId(), r.getRecordDate().toString(),
							truncate(r.getContent(), CONTENT_MAX), truncate(r.getDifficulty(), DIFFICULTY_MAX)))
					.toList();
			CoachPayload payload = new CoachPayload(mode, hobbies, (int) daysSinceLastRecord, payloadRecords);

			AiCallResult result = aiCallService.call(userId, AiResultType.AUTO_COACH, systemPrompt, payload);
			StoredCoachResult stored = validate(result.content(), mode, inputById.keySet(), inputRecords.size());

			AiResult saved = new AiResult(userId, null, AiResultType.COACH.name(), start, today, toJson(stored),
					toJson(inputRecords.stream().map(ActivityRecord::getId).toList()), cacheKey, PROMPT_VERSION,
					result.model());
			saved.markAuto();
			aiResultRepository.save(saved);
			notificationSchedulerService.notifyCoachArrival(userId);

			return toResponse(saved, false, userId, stored, inputById);
		} catch (ApiException e) {
			// AI 꺼짐·동시 호출 제한·한도 소진·검증 실패 모두 사용자에게는 오류로 보이지 않아야 한다(홈 진입을 막지 않음).
			return AiCoachResponse.locked(REQUIRED_RECORD_COUNT, periodRecords.size());
		} catch (java.io.IOException e) {
			return AiCoachResponse.locked(REQUIRED_RECORD_COUNT, periodRecords.size());
		}
	}

	private List<ComparePayloadHobby> distinctHobbies(Long userId, List<ActivityRecord> records) {
		Set<Long> hobbyIds = new java.util.LinkedHashSet<>();
		records.forEach(r -> hobbyIds.add(r.getHobbyId()));
		List<ComparePayloadHobby> hobbies = new ArrayList<>();
		for (Long hobbyId : hobbyIds) {
			hobbyRepository.findByIdAndUserId(hobbyId, userId)
					.ifPresent(h -> hobbies.add(new ComparePayloadHobby(h.getName(), h.getGoal())));
		}
		return hobbies;
	}

	private LocalDate periodStart(String period, LocalDate today) {
		return switch (period) {
			case "THIS_WEEK" -> today.minusDays(today.getDayOfWeek().getValue() - 1L);
			case "2W" -> today.minusDays(13);
			case "1M" -> today.minusDays(29);
			default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "period 값이 올바르지 않아요");
		};
	}

	private StoredCoachResult validate(String rawJson, String mode, Set<Long> allowedIds, int recordsRead) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "periodSummary", "changes", "repeatedDifficulties", "action");

		JsonNode summaryNode = node.path("periodSummary");
		if (!summaryNode.isTextual()) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		String periodSummary = summaryNode.asText().trim();
		if (periodSummary.isEmpty() || periodSummary.length() > PERIOD_SUMMARY_MAX
				|| containsForbiddenWord(periodSummary)) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		List<StoredCoachTextItem> changes = sanitizeTextItems(node.path("changes"), allowedIds);
		List<StoredCoachTextItem> repeatedDifficulties = sanitizeTextItems(node.path("repeatedDifficulties"), allowedIds);
		StoredCoachAction action = sanitizeAction(node.path("action"), mode, allowedIds);

		StoredCoachAction nextAction = mode.equals("NORMAL") ? action : null;
		StoredCoachAction comebackAction = mode.equals("COMEBACK") ? action : null;

		return new StoredCoachResult(mode, periodSummary, changes, repeatedDifficulties, nextAction, comebackAction,
				recordsRead);
	}

	private List<StoredCoachTextItem> sanitizeTextItems(JsonNode arrayNode, Set<Long> allowedIds) {
		List<StoredCoachTextItem> result = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return result;
		}
		for (JsonNode itemNode : arrayNode) {
			JsonNode textNode = itemNode.path("text");
			if (!textNode.isTextual()) {
				continue;
			}
			String text = textNode.asText().trim();
			if (text.isEmpty() || text.length() > TEXT_ITEM_MAX || containsForbiddenWord(text)) {
				continue;
			}
			List<Long> ids = sanitizeIds(itemNode.path("recordIds"), allowedIds);
			if (ids.isEmpty()) {
				continue;
			}
			result.add(new StoredCoachTextItem(text, ids));
		}
		return result;
	}

	private StoredCoachAction sanitizeAction(JsonNode actionNode, String mode, Set<Long> allowedIds) {
		if (actionNode.isMissingNode() || !actionNode.isObject()) {
			return null;
		}
		JsonNode titleNode = actionNode.path("title");
		JsonNode reasonNode = actionNode.path("reason");
		if (!titleNode.isTextual()) {
			return null;
		}
		String title = titleNode.asText().trim();
		String reason = reasonNode.isTextual() ? reasonNode.asText().trim() : "";
		if (title.isEmpty() || title.length() > ACTION_TITLE_MAX || containsForbiddenWord(title)
				|| reason.length() > ACTION_REASON_MAX || containsForbiddenWord(reason)) {
			return null;
		}
		int minutesRaw = actionNode.path("minutes").asInt(0);
		int[] range = mode.equals("COMEBACK") ? new int[] {1, 5} : new int[] {5, 10};
		int minutes = Math.min(Math.max(minutesRaw, range[0]), range[1]);
		List<Long> ids = sanitizeIds(actionNode.path("recordIds"), allowedIds);
		return new StoredCoachAction(title, minutes, reason, ids);
	}

	private List<Long> sanitizeIds(JsonNode idsNode, Set<Long> allowedIds) {
		List<Long> result = new ArrayList<>();
		if (!idsNode.isArray()) {
			return result;
		}
		for (JsonNode idNode : idsNode) {
			if (!idNode.canConvertToLong()) {
				continue;
			}
			long id = idNode.asLong();
			if (allowedIds.contains(id) && !result.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

	private boolean containsForbiddenWord(String text) {
		return FORBIDDEN_WORDS.stream().anyMatch(text::contains);
	}

	private String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("COACH 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	private StoredCoachResult readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredCoachResult.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "저장된 결과를 읽지 못했어요");
		}
	}

	@Transactional(readOnly = true)
	public AiCoachResponse getById(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COACH.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return toResponse(result, true, userId);
	}

	@Transactional
	public AiCoachResponse submitFeedback(Long userId, Long id, String value) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COACH.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.setFeedback(value);
		return toResponse(result, true, userId);
	}

	/** 자동 회고를 처음 열어본 시각을 기록한다(멱등: 이미 봤으면 그대로 둔다). 내 결과에만. */
	@Transactional
	public AiCoachResponse markViewed(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COACH.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.markViewed(java.time.LocalDateTime.now(SERVER_ZONE));
		return toResponse(result, true, userId);
	}

	/** nextAction의 "해봤어요·오늘은 쉴게요·너무 어려워요" 반응. 내 결과에만, 값은 그대로 저장한다. */
	@Transactional
	public AiCoachResponse submitReaction(Long userId, Long id, String value) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COACH.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.setReaction(value, java.time.LocalDateTime.now(SERVER_ZONE));
		return toResponse(result, true, userId);
	}

	@Transactional(readOnly = true)
	public AiCoachHistoryResponse history(Long userId, Long hobbyId, int page, int size) {
		var pageable = org.springframework.data.domain.PageRequest.of(page, size);
		List<AiResult> results = hobbyId != null
				? aiResultRepository.findByUserIdAndResultTypeAndHobbyIdOrderByCreatedAtDesc(userId,
						AiResultType.COACH.name(), hobbyId, pageable)
				: aiResultRepository.findByUserIdAndResultTypeOrderByCreatedAtDesc(userId, AiResultType.COACH.name(),
						pageable);
		long totalCount = hobbyId != null
				? aiResultRepository.countByUserIdAndResultTypeAndHobbyId(userId, AiResultType.COACH.name(), hobbyId)
				: aiResultRepository.countByUserIdAndResultType(userId, AiResultType.COACH.name());
		return new AiCoachHistoryResponse(results.stream().map(r -> toResponse(r, true, userId)).toList(), totalCount);
	}

	/**
	 * "너무 어려워요"를 실제로 반영한다. 결과당 1회만 되고(이미 줄였으면 409),
	 * 근거는 원래 행동의 recordIds 중 최대 5개로 좁혀 모델에 넘긴다(새 근거를 만들지 않는다).
	 */
	@Transactional
	public AiCoachResponse simplify(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COACH.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));

		StoredCoachResult stored = readJson(result.getResult());
		if (stored.simplifiedAction() != null) {
			throw new ApiException(ErrorCode.AI_ALREADY_SIMPLIFIED, "이미 더 작게 바꿨어요. 지금은 쉬어도 괜찮아요.");
		}
		StoredCoachAction original = stored.nextAction() != null ? stored.nextAction() : stored.comebackAction();
		if (original == null) {
			throw new ApiException(ErrorCode.NOT_FOUND, "더 작게 바꿀 행동이 없어요");
		}

		List<Long> evidenceIds = original.recordIds().stream().limit(MAX_SIMPLIFY_EVIDENCE).toList();
		List<ActivityRecord> evidenceRecords = evidenceIds.stream()
				.map(evidenceId -> activityRecordRepository.findByIdAndUserId(evidenceId, userId).orElse(null))
				.filter(java.util.Objects::nonNull)
				.toList();

		String systemPrompt;
		try {
			systemPrompt = simplifyPromptLoader.load();
		} catch (java.io.IOException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		List<ComparePayloadRecord> payloadRecords = evidenceRecords.stream()
				.map(r -> new ComparePayloadRecord(r.getId(), r.getRecordDate().toString(),
						truncate(r.getContent(), CONTENT_MAX), truncate(r.getDifficulty(), DIFFICULTY_MAX)))
				.toList();
		SimplifyPayload payload = new SimplifyPayload(original.title(), original.minutes(), original.reason(),
				payloadRecords);

		AiCallResult callResult = aiCallService.call(userId, AiResultType.SIMPLIFY, systemPrompt, payload);
		Set<Long> allowedIds = new java.util.HashSet<>(evidenceIds);
		StoredCoachAction simplified = validateSimplifiedAction(callResult.content(), allowedIds, evidenceIds);

		StoredCoachResult updated = new StoredCoachResult(stored.mode(), stored.periodSummary(), stored.changes(),
				stored.repeatedDifficulties(), stored.nextAction(), stored.comebackAction(), stored.recordsRead(),
				simplified);
		result.setResult(toJson(updated));
		result.setReaction(null, null);
		return toResponse(result, true, userId);
	}

	private StoredCoachAction validateSimplifiedAction(String rawJson, Set<Long> allowedIds,
			List<Long> fallbackIds) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "title", "minutes", "reason");

		JsonNode titleNode = node.path("title");
		JsonNode reasonNode = node.path("reason");
		if (!titleNode.isTextual()) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		String title = titleNode.asText().trim();
		String reason = reasonNode.isTextual() ? reasonNode.asText().trim() : "";
		if (title.isEmpty() || title.length() > ACTION_TITLE_MAX || containsForbiddenWord(title)
				|| reason.length() > ACTION_REASON_MAX || containsForbiddenWord(reason)) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		int minutesRaw = node.path("minutes").asInt(SIMPLIFY_MIN_MINUTES);
		int minutes = Math.min(Math.max(minutesRaw, SIMPLIFY_MIN_MINUTES), SIMPLIFY_MAX_MINUTES);
		List<Long> ids = sanitizeIds(node.path("recordIds"), allowedIds);
		if (ids.isEmpty()) {
			ids = fallbackIds;
		}
		return new StoredCoachAction(title, minutes, reason, ids);
	}

	@Transactional(readOnly = true)
	public AiCoachResponse latest(Long userId) {
		List<AiResult> results = aiResultRepository.findByUserIdAndResultTypeOrderByCreatedAtDesc(userId,
				AiResultType.COACH.name(), org.springframework.data.domain.PageRequest.of(0, 1));
		return results.isEmpty() ? null : toResponse(results.get(0), true, userId);
	}

	private AiCoachResponse toResponse(AiResult result, boolean reused, Long userId) {
		StoredCoachResult stored = readJson(result.getResult());
		return toResponse(result, reused, userId, stored, null);
	}

	private AiCoachResponse toResponse(AiResult result, boolean reused, Long userId, StoredCoachResult stored,
			Map<Long, ActivityRecord> preloaded) {
		// simplifiedAction이 있으면 그 자리(nextAction 또는 comebackAction, 원래 둘 중 하나만 채워져 있다)를
		// 현재 행동으로 보여준다. 원래 행동은 stored JSON에는 그대로 남아 있지만 응답에는 내보내지 않는다.
		boolean nextIsActive = stored.nextAction() != null;
		StoredCoachAction currentNextAction = nextIsActive && stored.simplifiedAction() != null
				? stored.simplifiedAction() : stored.nextAction();
		StoredCoachAction currentComebackAction = !nextIsActive && stored.simplifiedAction() != null
				? stored.simplifiedAction() : stored.comebackAction();
		return new AiCoachResponse(
				false, null, null,
				result.getId(),
				stored.mode(),
				result.getStartDate(),
				result.getEndDate(),
				result.getCreatedAt(),
				reused,
				stored.periodSummary(),
				stored.changes().stream().map(item -> toTextItemResponse(item, userId, preloaded)).toList(),
				stored.repeatedDifficulties().stream().map(item -> toTextItemResponse(item, userId, preloaded)).toList(),
				toActionResponse(currentNextAction, userId, preloaded),
				toActionResponse(currentComebackAction, userId, preloaded),
				stored.recordsRead(),
				result.getFeedback(),
				result.getReaction(),
				computeCardState(result, stored.simplifiedAction() != null),
				result.isAuto() && result.getViewedAt() == null);
	}

	/**
	 * 홈에서 카드를 어떻게 보여줄지 서버 시간대(Asia/Seoul) 기준으로 정한다. 생성 후 7일이 지났거나
	 * "해봤어요" 반응이 어제 이전이면 EXPIRED가 다른 상태보다 먼저다. "오늘은 쉴게요"는 그날 하루만
	 * 카드를 접어 두고 다음 날엔 다시 ACTIVE로 돌아온다. 이미 한 번 줄였는데 또 "너무 어려워요"가
	 * 오면 더 줄이는 루프를 만들지 않고 RESTING으로 끝맺는다.
	 */
	private String computeCardState(AiResult result, boolean alreadySimplified) {
		LocalDate today = LocalDate.now(SERVER_ZONE);
		LocalDate createdDate = result.getCreatedAt().toLocalDate();
		if (ChronoUnit.DAYS.between(createdDate, today) > 7) {
			return "EXPIRED";
		}
		String reaction = result.getReaction();
		LocalDate reactedDate = result.getReactedAt() == null ? null : result.getReactedAt().toLocalDate();
		if ("TRIED".equals(reaction)) {
			return today.equals(reactedDate) ? "CELEBRATE" : "EXPIRED";
		}
		if ("REST_TODAY".equals(reaction)) {
			return today.equals(reactedDate) ? "RESTING" : "ACTIVE";
		}
		if ("TOO_HARD".equals(reaction) && alreadySimplified) {
			return "RESTING";
		}
		return "ACTIVE";
	}

	private CoachTextItemResponse toTextItemResponse(StoredCoachTextItem item, Long userId,
			Map<Long, ActivityRecord> preloaded) {
		return new CoachTextItemResponse(item.text(), toEvidence(item.recordIds(), userId, preloaded));
	}

	private CoachActionResponse toActionResponse(StoredCoachAction action, Long userId,
			Map<Long, ActivityRecord> preloaded) {
		if (action == null) {
			return null;
		}
		return new CoachActionResponse(action.title(), action.minutes(), action.reason(),
				toEvidence(action.recordIds(), userId, preloaded));
	}

	private List<CoachEvidence> toEvidence(List<Long> ids, Long userId, Map<Long, ActivityRecord> preloaded) {
		List<CoachEvidence> evidence = new ArrayList<>();
		for (Long id : ids) {
			ActivityRecord record = preloaded != null && preloaded.containsKey(id)
					? preloaded.get(id)
					: activityRecordRepository.findByIdAndUserId(id, userId).orElse(null);
			if (record != null) {
				evidence.add(new CoachEvidence(record.getId(), record.getRecordDate(), record.getContent()));
			}
		}
		return evidence;
	}
}
