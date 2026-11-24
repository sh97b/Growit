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
	/** 글자 수 하드 한도. 프롬프트에는 headline 60자·각 문장 70자 이내를 목표로 적고, 서버는 이만큼까지 받아들인다. */
	private static final int HEADLINE_HARD_MAX = 100;
	private static final int ITEM_HARD_MAX = 110;
	private static final int RAW_LOG_MAX = 150;
	private static final String STRUCTURED_SCHEMA_NAME = "compare_insights";
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CompareService.class);
	private static final int GOOD_MAX = 3;
	private static final int GAP_MAX = 2;
	private static final int NEXT_MAX = 2;
	private static final int EVIDENCE_SNIPPET_MAX = 40;
	private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");
	/** prompts/ai-compare-system.txt를 바꿀 때마다, 또는 기준일 규칙처럼 저장된 결과의 모양이 바뀌면 올린다. */
	static final int PROMPT_VERSION = 5;
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

		// 새 구조(json_schema)로 먼저 부른다. 검증에 실패하면 어떤 필드가 왜 거절됐는지 한 줄을 붙여 같은 방식으로 한 번 더 부르고
		// (하루 한도에 세지 않는다), 그래도 안 되면 예전 한 문단 방식으로 대체한다.
		java.util.Map<String, Object> schema = structuredSchema();
		AiCallResult result = aiCallService.call(userId, AiResultType.COMPARE, systemPrompt, payload, false,
				STRUCTURED_SCHEMA_NAME, schema);
		StructuredCheck check = validateStructured(result.content(), past, current);
		if (check.stored() == null) {
			String hint = systemPrompt + "\n\n[교정 지시] 직전 응답의 " + check.field() + " 필드가 " + reasonText(check.reason())
					+ " 때문에 거절됐어요. 같은 입력으로 규칙을 지켜 JSON 객체 하나만 다시 응답해요.";
			result = aiCallService.call(userId, AiResultType.COMPARE, hint, payload, true, STRUCTURED_SCHEMA_NAME, schema);
			check = validateStructured(result.content(), past, current);
		}
		StoredCompareResult stored = check.stored();
		if (stored == null) {
			log.warn("COMPARE_FALLBACK_LEGACY reason={}:{}", check.field(), check.reason());
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

	/** 구조화 검증 결과. stored가 null이면 전체 실패이고 field·reason이 첫 실패 지점이다. */
	record StructuredCheck(StoredCompareResult stored, String field, String reason) {
		static StructuredCheck ok(StoredCompareResult stored) {
			return new StructuredCheck(stored, null, null);
		}

		static StructuredCheck failed(String field, String reason) {
			return new StructuredCheck(null, field, reason);
		}
	}

	/** 한 문장 검사 결과: value가 null이면 reason(LENGTH|FIELD|FORBIDDEN|NUMBER)이 거절 이유다. */
	private record Text(String value, String reason, int length) {
	}

	/** 항목 하나가 버려질 때 첫 사유를 field별로 기억해 두었다가, 그 field가 통째로 비면 보고한다. */
	private static final class Drops {
		private final java.util.Map<String, String> firstReason = new java.util.HashMap<>();

		void add(String field, String reason) {
			firstReason.putIfAbsent(field, reason);
		}

		String reasonOf(String field) {
			return firstReason.getOrDefault(field, "FIELD");
		}
	}

	/**
	 * 구조화 응답을 검증한다. 검증을 통과하지 못한 항목만 버리고(버릴 때마다 COMPARE_STRUCT_DROPPED를 남긴다), headline이나
	 * 좋아진 점(good)이 하나도 안 남으면 전체 실패(StructuredCheck.failed)다. 근거 id가 비었거나 잘못됐으면 항목을 버리지 않고
	 * 비교한 두 기록 중 최근 기록 id로 대신한다. 글에 나온 숫자(아라비아 숫자와 한글 수사)는 입력 기록의 본문·시간·날짜에 있는 숫자만 허용한다.
	 */
	StructuredCheck validateStructured(String rawJson, ActivityRecord past, ActivityRecord current) {
		List<Long> recordIds = List.of(past.getId(), current.getId());
		JsonNode node;
		try {
			node = aiResponseValidator.parse(rawJson);
		} catch (ApiException e) {
			logDropped(recordIds, "headline", "PARSE", rawJson == null ? 0 : rawJson.length(), rawJson);
			return StructuredCheck.failed("headline", "PARSE");
		}
		Set<Long> allowed = Set.of(past.getId(), current.getId());
		Set<Integer> allowedNumbers = allowedNumbers(past, current);
		Drops drops = new Drops();

		Text headline = cleanText(node.path("headline"), HEADLINE_HARD_MAX, allowedNumbers);
		if (headline.value() == null) {
			logDropped(recordIds, "headline", headline.reason(), headline.length(), rawJson);
			return StructuredCheck.failed("headline", headline.reason());
		}
		List<StoredCompareInsight> good = insights("good", node.path("good"), GOOD_MAX, allowed, current, allowedNumbers,
				recordIds, rawJson, drops);
		if (good.isEmpty()) {
			String reason = node.path("good").isArray() ? drops.reasonOf("good") : "FIELD";
			if (!node.path("good").isArray()) {
				logDropped(recordIds, "good", "FIELD", 0, rawJson);
			}
			return StructuredCheck.failed("good", reason);
		}
		List<StoredCompareInsight> gap = insights("gap", node.path("gap"), GAP_MAX, allowed, current, allowedNumbers,
				recordIds, rawJson, drops);
		List<StoredCompareIdea> next = ideas(node.path("next"), NEXT_MAX, allowed, current, allowedNumbers, recordIds,
				rawJson);
		log.info("COMPARE_STRUCT_OK good={} gap={} next={}", good.size(), gap.size(), next.size());
		// 구조화 결과는 비교한 두 기록을 항상 함께 보여 준다(근거로 어느 쪽을 몇 번 썼는지와 상관없이).
		return StructuredCheck.ok(new StoredCompareResult(headline.value(), past.getId(), current.getId(), null, null,
				false, false, STRUCTURED_SCHEMA_VERSION, headline.value(), good, gap, next));
	}

	private List<StoredCompareInsight> insights(String field, JsonNode arrayNode, int max, Set<Long> allowed,
			ActivityRecord current, Set<Integer> allowedNumbers, List<Long> recordIds, String rawJson, Drops drops) {
		List<StoredCompareInsight> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		for (JsonNode item : arrayNode) {
			if (kept.size() >= max) {
				break;
			}
			Text point = cleanText(item.path("point"), ITEM_HARD_MAX, allowedNumbers);
			if (point.value() == null) {
				drop(field, point, recordIds, rawJson, drops);
				continue;
			}
			Text because = cleanText(item.path("because"), ITEM_HARD_MAX, allowedNumbers);
			if (because.value() == null) {
				drop(field, because, recordIds, rawJson, drops);
				continue;
			}
			kept.add(new StoredCompareInsight(point.value(), because.value(),
					evidenceIds(item.path("evidenceRecordIds"), allowed, current)));
		}
		return kept;
	}

	private List<StoredCompareIdea> ideas(JsonNode arrayNode, int max, Set<Long> allowed, ActivityRecord current,
			Set<Integer> allowedNumbers, List<Long> recordIds, String rawJson) {
		List<StoredCompareIdea> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		for (JsonNode item : arrayNode) {
			if (kept.size() >= max) {
				break;
			}
			Text idea = cleanText(item.path("idea"), ITEM_HARD_MAX, allowedNumbers);
			if (idea.value() == null) {
				logDropped(recordIds, "next", idea.reason(), idea.length(), rawJson);
				continue;
			}
			kept.add(new StoredCompareIdea(idea.value(), evidenceIds(item.path("evidenceRecordIds"), allowed, current)));
		}
		return kept;
	}

	private void drop(String field, Text text, List<Long> recordIds, String rawJson, Drops drops) {
		drops.add(field, text.reason());
		logDropped(recordIds, field, text.reason(), text.length(), rawJson);
	}

	private void logDropped(List<Long> recordIds, String field, String reason, int length, String rawJson) {
		String raw = rawJson == null ? "" : rawJson.replaceAll("\\s+", " ");
		if (raw.length() > RAW_LOG_MAX) {
			raw = raw.substring(0, RAW_LOG_MAX);
		}
		log.warn("COMPARE_STRUCT_DROPPED recordIds={} field={} reason={} length={} raw={}", recordIds, field, reason,
				length, raw);
	}

	/** 허용된 id만 남기고, 하나도 안 남으면(비었거나 남의·없는 id) 비교한 두 기록 중 최근 기록 id로 대신한다. */
	private List<Long> evidenceIds(JsonNode idsNode, Set<Long> allowed, ActivityRecord current) {
		List<Long> ids = new ArrayList<>();
		if (idsNode.isArray()) {
			for (JsonNode idNode : idsNode) {
				if (idNode.canConvertToLong() && allowed.contains(idNode.asLong()) && !ids.contains(idNode.asLong())) {
					ids.add(idNode.asLong());
				}
			}
		}
		if (ids.isEmpty()) {
			ids.add(current.getId());
		}
		return ids;
	}

	/** 문자열이 아니거나 비었으면 FIELD, 길이를 넘으면 LENGTH, 금지어면 FORBIDDEN, 기록에 없는 숫자가 있으면 NUMBER. */
	private Text cleanText(JsonNode node, int maxLength, Set<Integer> allowedNumbers) {
		if (!node.isTextual()) {
			return new Text(null, "FIELD", 0);
		}
		String text = node.asText().trim();
		if (text.isEmpty()) {
			return new Text(null, "FIELD", 0);
		}
		if (text.length() > maxLength) {
			return new Text(null, "LENGTH", text.length());
		}
		if (containsForbiddenWord(text)) {
			return new Text(null, "FORBIDDEN", text.length());
		}
		for (int number : numbersIn(text)) {
			// 한·두(1·2)는 "한 번", "두 기록"처럼 숫자가 아닌 말로도 흔히 쓰여 검사하지 않는다.
			if (number > 2 && !allowedNumbers.contains(number)) {
				return new Text(null, "NUMBER", text.length());
			}
		}
		return new Text(text, null, text.length());
	}

	/** 입력(두 기록의 본문·어려움·시간·날짜)에 나온 숫자 전부(아라비아 숫자와 한글 수사). */
	private Set<Integer> allowedNumbers(ActivityRecord past, ActivityRecord current) {
		Set<Integer> numbers = new java.util.HashSet<>();
		for (ActivityRecord record : List.of(past, current)) {
			numbers.addAll(numbersIn(record.getContent()));
			numbers.addAll(numbersIn(record.getDifficulty()));
			if (record.getMinutes() != null) {
				numbers.add(record.getMinutes());
			}
			numbers.add(record.getRecordDate().getYear());
			numbers.add(record.getRecordDate().getMonthValue());
			numbers.add(record.getRecordDate().getDayOfMonth());
		}
		return numbers;
	}

	private static final java.util.Map<String, Integer> NATIVE_TENS = java.util.Map.of("열", 10, "스물", 20, "스무", 20,
			"서른", 30, "마흔", 40, "쉰", 50);
	private static final java.util.Map<String, Integer> NATIVE_UNITS = java.util.Map.ofEntries(
			java.util.Map.entry("하나", 1), java.util.Map.entry("한", 1), java.util.Map.entry("둘", 2),
			java.util.Map.entry("두", 2), java.util.Map.entry("셋", 3), java.util.Map.entry("세", 3),
			java.util.Map.entry("넷", 4), java.util.Map.entry("네", 4), java.util.Map.entry("다섯", 5),
			java.util.Map.entry("여섯", 6), java.util.Map.entry("일곱", 7), java.util.Map.entry("여덟", 8),
			java.util.Map.entry("아홉", 9));
	/** 한글 수사는 뒤에 셈 단위가 붙은 것만 숫자로 본다("한국", "세계" 같은 말을 숫자로 읽지 않으려고). */
	private static final Pattern KOREAN_COUNT = Pattern.compile(
			"(?<![가-힣])(열|스물|스무|서른|마흔|쉰)?(하나|한|둘|두|셋|세|넷|네|다섯|여섯|일곱|여덟|아홉)?\\s?"
					+ "(번째|번|개|분|시간|곡|줄|쪽|주|달|가지|마디|장|회|차례|곳|배|명|권)");

	/** 글 안의 숫자를 정수 집합으로 뽑는다: 아라비아 숫자("10번")와 한글 수사("열 번", "여섯 개"). */
	static Set<Integer> numbersIn(String text) {
		Set<Integer> numbers = new java.util.HashSet<>();
		if (text == null) {
			return numbers;
		}
		Matcher digits = NUMBER_PATTERN.matcher(text);
		while (digits.find()) {
			String value = digits.group();
			if (value.length() <= 9) {
				numbers.add(Integer.parseInt(value));
			}
		}
		Matcher korean = KOREAN_COUNT.matcher(text);
		while (korean.find()) {
			String tens = korean.group(1);
			String units = korean.group(2);
			if (tens == null && units == null) {
				continue;
			}
			int value = (tens == null ? 0 : NATIVE_TENS.get(tens)) + (units == null ? 0 : NATIVE_UNITS.get(units));
			numbers.add(value);
		}
		return numbers;
	}

	/** 구조화 응답의 JSON 스키마(strict): 모든 칸 required, additionalProperties=false. */
	static java.util.Map<String, Object> structuredSchema() {
		java.util.Map<String, Object> string = java.util.Map.of("type", "string");
		java.util.Map<String, Object> ids = java.util.Map.of("type", "array", "items", java.util.Map.of("type", "integer"));
		java.util.Map<String, Object> insight = java.util.Map.of("type", "object", "additionalProperties", false,
				"required", List.of("point", "because", "evidenceRecordIds"),
				"properties", java.util.Map.of("point", string, "because", string, "evidenceRecordIds", ids));
		java.util.Map<String, Object> idea = java.util.Map.of("type", "object", "additionalProperties", false,
				"required", List.of("idea", "evidenceRecordIds"),
				"properties", java.util.Map.of("idea", string, "evidenceRecordIds", ids));
		return java.util.Map.of("type", "object", "additionalProperties", false,
				"required", List.of("headline", "good", "gap", "next"),
				"properties", java.util.Map.of("headline", string,
						"good", java.util.Map.of("type", "array", "items", insight),
						"gap", java.util.Map.of("type", "array", "items", insight),
						"next", java.util.Map.of("type", "array", "items", idea)));
	}

	private static String reasonText(String reason) {
		return switch (reason) {
			case "PARSE" -> "JSON 형식이 아니었기";
			case "LENGTH" -> "글자 수가 너무 많았기";
			case "FORBIDDEN" -> "쓰면 안 되는 표현이 있었기";
			case "NUMBER" -> "기록에 없는 숫자가 있었기";
			case "EVIDENCE" -> "근거 기록이 올바르지 않았기";
			default -> "비어 있거나 형식이 맞지 않았기";
		};
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
