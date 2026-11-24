package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.AiCompareResponse;
import com.growit.ai.dto.CompareEvidence;
import com.growit.ai.dto.CompareIdea;
import com.growit.ai.dto.CompareInsight;
import com.growit.ai.dto.CompareMetrics;
import com.growit.ai.dto.CompareRecordCard;
import com.growit.ai.dto.CompareRequest;
import com.growit.ai.dto.MetricPair;
import com.growit.ai.dto.TopicCount;
import com.growit.ai.entity.AiResult;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiResultRepository;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.repository.ActivityRecordRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompareService {

	private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");
	private static final int REQUIRED_RECORD_COUNT = 3;
	/** 과거 기록을 못 찾을 때 가장 오래된 기록으로 대신 쓰려면(pastIsOldest) 현재 기록과 최소 이만큼 떨어져 있어야 한다. */
	private static final int MIN_OLDEST_FALLBACK_GAP_DAYS = 3;
	private static final int CONTENT_MAX = 300;
	private static final int DIFFICULTY_MAX = 200;
	private static final int INTERPRETATION_MAX = 200;
	private static final String PROMPT_RESOURCE_NAME = "ai-compare-system.txt";
	/** 구조화 결과를 하나도 못 쓸 때 대신 쓰는 예전(한 문단) 프롬프트. */
	private static final String LEGACY_PROMPT_RESOURCE_NAME = "ai-compare-legacy-system.txt";
	/** 새 결과의 schemaVersion. 예전 결과는 null이다. */
	static final int STRUCTURED_SCHEMA_VERSION = 2;
	private static final int HEADLINE_MAX = 60;
	private static final int ITEM_TEXT_MAX = 70;
	private static final int GOOD_MAX = 3;
	private static final int GAP_MAX = 2;
	private static final int NEXT_MAX = 2;
	private static final int EVIDENCE_SNIPPET_MAX = 40;
	private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
	/** prompts/ai-compare-system.txt를 바꿀 때마다, 또는 기준일 규칙처럼 저장된 결과의 모양이 바뀌면 올린다. */
	static final int PROMPT_VERSION = 4;
	private static final Set<String> VALID_COMPARE_TO = Set.of("1W", "2W", "1M", "3M");
	private static final List<String> FORBIDDEN_WORDS =
			List.of("실력", "늘었", "향상", "잘했", "훌륭", "대단", "점수", "순위", "완벽", "최고", "천재", "반드시",
					"오늘 안에", "연속");

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiResultRepository aiResultRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final ObjectMapper objectMapper;

	PromptResourceLoader promptLoader = new PromptResourceLoader(PROMPT_RESOURCE_NAME);
	PromptResourceLoader legacyPromptLoader = new PromptResourceLoader(LEGACY_PROMPT_RESOURCE_NAME);

	public CompareService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			AiResultRepository aiResultRepository, AiCallService aiCallService,
			AiResponseValidator aiResponseValidator, ObjectMapper objectMapper) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.aiResultRepository = aiResultRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public AiCompareResponse compare(Long userId, CompareRequest request) {
		Hobby hobby = hobbyRepository.findByIdAndUserId(request.hobbyId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		if (!VALID_COMPARE_TO.contains(request.compareTo())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "compareTo 값이 올바르지 않아요");
		}

		long totalCount = activityRecordRepository.countByUserIdAndHobbyId(userId, hobby.getId());
		if (totalCount < REQUIRED_RECORD_COUNT) {
			return AiCompareResponse.lockedNotEnoughRecords(REQUIRED_RECORD_COUNT, (int) totalCount);
		}

		ActivityRecord current = activityRecordRepository
				.findFirstByUserIdAndHobbyIdOrderByRecordDateDescIdDesc(userId, hobby.getId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없어요"));
		LocalDate baseDate = current.getRecordDate();
		LocalDate targetDate = applyOffset(baseDate, request.compareTo());

		ActivityRecord past = activityRecordRepository
				.findFirstByUserIdAndHobbyIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(
						userId, hobby.getId(), targetDate)
				.filter(record -> !record.getId().equals(current.getId()))
				.orElse(null);

		boolean pastIsOldest = false;
		if (past == null) {
			ActivityRecord oldest = activityRecordRepository
					.findFirstByUserIdAndHobbyIdOrderByRecordDateAscIdAsc(userId, hobby.getId())
					.orElse(null);
			long gapDays = oldest == null ? 0
					: java.time.temporal.ChronoUnit.DAYS.between(oldest.getRecordDate(), current.getRecordDate());
			if (oldest != null && !oldest.getId().equals(current.getId()) && gapDays >= MIN_OLDEST_FALLBACK_GAP_DAYS) {
				past = oldest;
				pastIsOldest = true;
			} else {
				LocalDate oldestDate = oldest == null ? null : oldest.getRecordDate();
				return AiCompareResponse.lockedNoPastRecord(REQUIRED_RECORD_COUNT, (int) totalCount, oldestDate,
						current.getRecordDate());
			}
		}

		boolean anchoredToLatest = !baseDate.equals(LocalDate.now(SERVER_ZONE));

		String cacheKey = CacheKeys.sha256(PROMPT_VERSION + "|COMPARE|" + hobby.getId()
				+ "|" + past.getId() + ":" + past.getUpdatedAt()
				+ "|" + current.getId() + ":" + current.getUpdatedAt());

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

		ComparePayload payload = new ComparePayload(
				new ComparePayloadHobby(hobby.getName(), hobby.getGoal()),
				toPayloadRecord(past),
				toPayloadRecord(current));

		AiCallResult result = aiCallService.call(userId, AiResultType.COMPARE, systemPrompt, payload);
		StoredCompareResult stored = validateStructured(result.content(), past, current);
		if (stored == null) {
			// headline이나 좋아진 점이 하나도 안 남았으면 예전 한 문단 방식으로 한 번 더 만든다(한도에는 세지 않는 교정 재호출).
			result = aiCallService.call(userId, AiResultType.COMPARE, loadLegacyPrompt(), payload, true);
			stored = validate(result.content(), past.getId(), current.getId());
		}

		CompareMetrics metrics = computeMetrics(userId, hobby.getId(), past.getRecordDate(), baseDate);
		StoredCompareResult withMetrics = new StoredCompareResult(stored.interpretation(), stored.pastRecordId(),
				stored.currentRecordId(), metrics, baseDate, anchoredToLatest, pastIsOldest, stored.schemaVersion(),
				stored.headline(), stored.good(), stored.gap(), stored.next());

		AiResult saved = new AiResult(userId, hobby.getId(), AiResultType.COMPARE.name(), past.getRecordDate(),
				baseDate, toJson(withMetrics), toJson(List.of(past.getId(), current.getId())), cacheKey,
				PROMPT_VERSION, result.model());
		aiResultRepository.save(saved);

		return toResponse(saved, false, userId, past, current);
	}

	/** 1W/2W는 일수, 1M/3M은 달력 월 단위로 뺀다(2월 같은 짧은 달을 자연스럽게 다룬다). */
	private LocalDate applyOffset(LocalDate base, String compareTo) {
		return switch (compareTo) {
			case "1W" -> base.minusDays(7);
			case "2W" -> base.minusDays(14);
			case "1M" -> base.minusMonths(1);
			case "3M" -> base.minusMonths(3);
			default -> throw new ApiException(ErrorCode.VALIDATION_ERROR, "compareTo 값이 올바르지 않아요");
		};
	}

	private ComparePayloadRecord toPayloadRecord(ActivityRecord record) {
		return new ComparePayloadRecord(record.getId(), record.getRecordDate().toString(),
				truncate(record.getContent(), CONTENT_MAX), truncate(record.getDifficulty(), DIFFICULTY_MAX));
	}

	private StoredCompareResult validate(String rawJson, Long pastId, Long currentId) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "interpretation", "pastRecordIds", "currentRecordIds");

		JsonNode interpretationNode = node.path("interpretation");
		if (!interpretationNode.isTextual()) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
		String interpretation = interpretationNode.asText().trim();
		if (interpretation.isEmpty() || interpretation.length() > INTERPRETATION_MAX
				|| containsForbiddenWord(interpretation)) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}

		Set<Long> allowed = Set.of(pastId, currentId);
		Long usedPastId = containsId(node.path("pastRecordIds"), pastId, allowed) ? pastId : null;
		Long usedCurrentId = containsId(node.path("currentRecordIds"), currentId, allowed) ? currentId : null;

		return new StoredCompareResult(interpretation, usedPastId, usedCurrentId, null, null, false, false, null, null,
				null, null, null);
	}

	private String loadLegacyPrompt() {
		try {
			return legacyPromptLoader.load();
		} catch (java.io.IOException e) {
			throw new ApiException(ErrorCode.AI_FAILED, "지금은 AI를 쓸 수 없어요. 기록은 안전하게 저장돼 있어요.");
		}
	}

	/**
	 * 구조화 응답을 검증한다. 검증을 통과하지 못한 항목만 버리고, headline이나 좋아진 점(good)이 하나도 안 남으면
	 * null을 돌려 호출한 쪽이 예전 방식으로 대체하게 한다. 항목의 근거 id는 이 요청에 넣어 준 두 기록(과거·현재)만
	 * 허용하고(다른 id가 하나라도 섞이면 그 항목을 버린다), 글에 나온 숫자는 두 기록에 적힌 숫자만 허용한다.
	 */
	StoredCompareResult validateStructured(String rawJson, ActivityRecord past, ActivityRecord current) {
		JsonNode node;
		try {
			node = aiResponseValidator.parse(rawJson);
		} catch (ApiException e) {
			return null;
		}
		Set<Long> allowed = Set.of(past.getId(), current.getId());
		Set<String> allowedNumbers = numbersIn(past.getContent(), past.getDifficulty(), current.getContent(),
				current.getDifficulty());

		String headline = cleanText(node.path("headline"), HEADLINE_MAX, allowedNumbers);
		List<StoredCompareInsight> good = insights(node.path("good"), GOOD_MAX, allowed, allowedNumbers);
		if (headline == null || good.isEmpty()) {
			return null;
		}
		List<StoredCompareInsight> gap = insights(node.path("gap"), GAP_MAX, allowed, allowedNumbers);
		List<StoredCompareIdea> next = ideas(node.path("next"), NEXT_MAX, allowed, allowedNumbers);

		// 구조화 결과는 비교한 두 기록을 항상 함께 보여 준다(근거로 어느 쪽을 몇 번 썼는지와 상관없이).
		return new StoredCompareResult(headline, past.getId(), current.getId(), null, null, false, false,
				STRUCTURED_SCHEMA_VERSION, headline, good, gap, next);
	}

	private List<StoredCompareInsight> insights(JsonNode arrayNode, int max, Set<Long> allowed,
			Set<String> allowedNumbers) {
		List<StoredCompareInsight> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		for (JsonNode item : arrayNode) {
			if (kept.size() >= max) {
				break;
			}
			String point = cleanText(item.path("point"), ITEM_TEXT_MAX, allowedNumbers);
			String because = cleanText(item.path("because"), ITEM_TEXT_MAX, allowedNumbers);
			List<Long> evidence = evidenceIds(item.path("evidenceRecordIds"), allowed);
			if (point != null && because != null && evidence != null) {
				kept.add(new StoredCompareInsight(point, because, evidence));
			}
		}
		return kept;
	}

	private List<StoredCompareIdea> ideas(JsonNode arrayNode, int max, Set<Long> allowed, Set<String> allowedNumbers) {
		List<StoredCompareIdea> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		for (JsonNode item : arrayNode) {
			if (kept.size() >= max) {
				break;
			}
			String idea = cleanText(item.path("idea"), ITEM_TEXT_MAX, allowedNumbers);
			List<Long> evidence = evidenceIds(item.path("evidenceRecordIds"), allowed);
			if (idea != null && evidence != null) {
				kept.add(new StoredCompareIdea(idea, evidence));
			}
		}
		return kept;
	}

	/** 근거가 없거나(빈 배열), 허용되지 않은 id(남의 기록 포함)가 하나라도 섞여 있으면 null이다. */
	private List<Long> evidenceIds(JsonNode idsNode, Set<Long> allowed) {
		if (!idsNode.isArray() || idsNode.isEmpty()) {
			return null;
		}
		List<Long> ids = new ArrayList<>();
		for (JsonNode idNode : idsNode) {
			if (!idNode.canConvertToLong() || !allowed.contains(idNode.asLong())) {
				return null;
			}
			if (!ids.contains(idNode.asLong())) {
				ids.add(idNode.asLong());
			}
		}
		return ids;
	}

	/** 문자열이 아니거나, 비었거나, 길이를 넘거나, 금지어가 있거나, 기록에 없는 숫자가 있으면 null이다. */
	private String cleanText(JsonNode node, int maxLength, Set<String> allowedNumbers) {
		if (!node.isTextual()) {
			return null;
		}
		String text = node.asText().trim();
		if (text.isEmpty() || text.length() > maxLength || containsForbiddenWord(text)) {
			return null;
		}
		Matcher matcher = NUMBER_PATTERN.matcher(text);
		while (matcher.find()) {
			if (!allowedNumbers.contains(matcher.group())) {
				return null;
			}
		}
		return text;
	}

	private Set<String> numbersIn(String... texts) {
		Set<String> numbers = new java.util.HashSet<>();
		for (String text : texts) {
			if (text == null) {
				continue;
			}
			Matcher matcher = NUMBER_PATTERN.matcher(text);
			while (matcher.find()) {
				numbers.add(matcher.group());
			}
		}
		return numbers;
	}

	private boolean containsId(JsonNode idsNode, long targetId, Set<Long> allowed) {
		if (!idsNode.isArray()) {
			return false;
		}
		for (JsonNode idNode : idsNode) {
			if (idNode.canConvertToLong() && idNode.asLong() == targetId && allowed.contains(targetId)) {
				return true;
			}
		}
		return false;
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

	private CompareMetrics computeMetrics(Long userId, Long hobbyId, LocalDate pastDate, LocalDate today) {
		long pastDays = activityRecordRepository.countDistinctRecordDateByUserIdAndHobbyIdUpTo(userId, hobbyId,
				pastDate);
		long currentDays = activityRecordRepository.countDistinctRecordDateByUserIdAndHobbyIdUpTo(userId, hobbyId,
				today);
		long pastMinutes = activityRecordRepository.sumMinutesByUserIdAndHobbyIdUpTo(userId, hobbyId, pastDate);
		long currentMinutes = activityRecordRepository.sumMinutesByUserIdAndHobbyIdUpTo(userId, hobbyId, today);
		List<TopicCount> topTags = computeTopTags(userId, hobbyId, pastDate, today);
		return new CompareMetrics(new MetricPair(pastDays, currentDays), new MetricPair(pastMinutes, currentMinutes),
				topTags);
	}

	/** 비교 기간(과거 시점~오늘) 안에서 ai_status=DONE인 기록의 analysis.tags로 주제 분포 상위 3개를 센다. */
	private List<TopicCount> computeTopTags(Long userId, Long hobbyId, LocalDate pastDate, LocalDate today) {
		List<ActivityRecord> periodRecords = activityRecordRepository
				.findByUserIdAndHobbyIdAndRecordDateBetween(userId, hobbyId, pastDate, today);
		Map<String, Long> counts = new java.util.LinkedHashMap<>();
		for (ActivityRecord record : periodRecords) {
			if (record.getAiStatus() != com.growit.record.entity.AiStatus.DONE) {
				continue;
			}
			for (String tag : extractTags(record.getAiAnalysis())) {
				counts.merge(tag, 1L, Long::sum);
			}
		}
		return counts.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(3)
				.map(entry -> new TopicCount(entry.getKey(), entry.getValue()))
				.toList();
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("COMPARE 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	private StoredCompareResult readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredCompareResult.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.NOT_FOUND, "저장된 결과를 읽지 못했어요");
		}
	}

	@Transactional(readOnly = true)
	public AiCompareResponse getById(Long userId, Long id) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COMPARE.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		return toResponse(result, true, userId);
	}

	/** 404로 남의 결과인지조차 알려주지 않는다. */
	@Transactional
	public AiCompareResponse submitFeedback(Long userId, Long id, String value) {
		AiResult result = aiResultRepository.findByIdAndUserId(id, userId)
				.filter(r -> r.getResultType().equals(AiResultType.COMPARE.name()))
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "결과를 찾을 수 없어요"));
		result.setFeedback(value);
		return toResponse(result, true, userId);
	}

	AiCompareResponse toResponse(AiResult result, boolean reused, Long userId) {
		StoredCompareResult stored = readJson(result.getResult());
		ActivityRecord past = activityRecordRepository.findByIdAndUserId(stored.pastRecordId(), userId).orElse(null);
		ActivityRecord current = activityRecordRepository.findByIdAndUserId(stored.currentRecordId(), userId)
				.orElse(null);
		return toResponse(result, reused, userId, past, current);
	}

	private AiCompareResponse toResponse(AiResult result, boolean reused, Long userId, ActivityRecord past,
			ActivityRecord current) {
		StoredCompareResult stored = readJson(result.getResult());
		boolean structured = stored.schemaVersion() != null && stored.headline() != null;
		return new AiCompareResponse(
				false, null, null, null, null, null,
				result.getId(),
				result.getStartDate(),
				result.getEndDate(),
				result.getCreatedAt(),
				reused,
				stored.baseDate(),
				stored.anchoredToLatest(),
				stored.pastIsOldest(),
				stored.interpretation(),
				past == null ? null : toCard(past),
				current == null ? null : toCard(current),
				stored.metrics(),
				result.getFeedback(),
				structured ? stored.schemaVersion() : null,
				structured ? stored.headline() : null,
				structured ? toInsights(stored.good(), userId) : null,
				structured ? toInsights(stored.gap(), userId) : null,
				structured ? toIdeas(stored.next(), userId) : null);
	}

	private List<CompareInsight> toInsights(List<StoredCompareInsight> items, Long userId) {
		if (items == null) {
			return List.of();
		}
		return items.stream()
				.map(item -> new CompareInsight(item.point(), item.because(), evidenceOf(item.evidenceRecordIds(), userId)))
				.toList();
	}

	private List<CompareIdea> toIdeas(List<StoredCompareIdea> items, Long userId) {
		if (items == null) {
			return List.of();
		}
		return items.stream()
				.map(item -> new CompareIdea(item.idea(), evidenceOf(item.evidenceRecordIds(), userId)))
				.toList();
	}

	/** 근거의 날짜·내용은 AI가 말한 것이 아니라 저장된 그 사용자의 기록에서 채운다(남의 기록이면 빠진다). */
	private List<CompareEvidence> evidenceOf(List<Long> ids, Long userId) {
		if (ids == null) {
			return List.of();
		}
		List<CompareEvidence> evidence = new ArrayList<>();
		for (Long id : ids) {
			activityRecordRepository.findByIdAndUserId(id, userId).ifPresent(record -> evidence.add(
					new CompareEvidence(record.getId(), record.getRecordDate(),
							truncate(record.getContent(), EVIDENCE_SNIPPET_MAX))));
		}
		return evidence;
	}

	private CompareRecordCard toCard(ActivityRecord record) {
		return new CompareRecordCard(record.getId(), record.getRecordDate(), record.getContent(),
				extractTags(record.getAiAnalysis()), record.getFeeling() == null ? null : record.getFeeling().name());
	}

	/** 기록 카드의 태그는 record 도메인의 저장 모양(StoredAnalysis)을 몰라도 되게, tags 칸만 가볍게 읽는다. */
	private List<String> extractTags(String aiAnalysisJson) {
		if (aiAnalysisJson == null) {
			return List.of();
		}
		try {
			JsonNode node = objectMapper.readTree(aiAnalysisJson).path("tags");
			if (!node.isArray()) {
				return List.of();
			}
			List<String> tags = new ArrayList<>();
			node.forEach(tagNode -> {
				if (tagNode.isTextual()) {
					tags.add(tagNode.asText());
				}
			});
			return tags;
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}
}
