package com.growit.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.dto.AiCompareResponse;
import com.growit.ai.dto.CompareBasisResponse;
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
	static final int PROMPT_VERSION = 6;
	private static final Set<String> VALID_COMPARE_TO = Set.of("1W", "2W", "1M", "3M");
	/**
	 * 금지어 A(능력·점수 단정, 평가 칭찬, 재촉·연속 압박): headline·good·gap·next 모두에 적용한다.
	 */
	static final List<String> FORBIDDEN_A = List.of("실력", "늘었", "향상", "점수", "순위", "잘했", "훌륭", "대단", "반드시",
			"오늘 안에", "연속");
	/**
	 * 금지어 B(과장·단정 예측): headline·good·next에만 적용하고, 아직 부족한 점을 부드럽게 말하는 gap에는 적용하지 않는다
	 * (예: "아직 완벽하지는 않아요").
	 */
	static final List<String> FORBIDDEN_B = List.of("완벽", "최고", "천재", "틀림없", "될 거예요", "보장");
	private static final int LOG_TEXT_MAX = 80;
	private static final int FALLBACK_DIFFICULTY_MAX = 20;
	/** 문장 끝에 붙은 평가 한 마디만 있는 꼬리를 잘라낼 때 쓰는 말(8자 이하). */
	private static final Pattern PRAISE_TAIL = Pattern.compile("^(정말 |참 |아주 )?(좋아요|멋져요|대단해요|훌륭해요|잘했어요)$");

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

	/** 비교할 두 기록을 고른 결과. locked가 있으면 비교할 수 없는 상태다(AI를 부르지 않는다). */
	private record Basis(AiCompareResponse locked, Hobby hobby, ActivityRecord past, ActivityRecord current,
			LocalDate baseDate, boolean pastIsOldest, boolean anchoredToLatest) {
	}

	/**
	 * 이전·최근 기록 선정과 잠금 판단. AI 해석(compare)과 기본 데이터(basis)가 같은 규칙을 쓰도록 한 곳에 둔다.
	 * 남의 분야는 존재를 숨기려고 NOT_FOUND다.
	 */
	private Basis resolveBasis(Long userId, CompareRequest request) {
		Hobby hobby = hobbyRepository.findByIdAndUserId(request.hobbyId(), userId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "활동 분야를 찾을 수 없어요"));
		if (!VALID_COMPARE_TO.contains(request.compareTo())) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "compareTo 값이 올바르지 않아요");
		}

		long totalCount = activityRecordRepository.countByUserIdAndHobbyId(userId, hobby.getId());
		if (totalCount < REQUIRED_RECORD_COUNT) {
			return new Basis(AiCompareResponse.lockedNotEnoughRecords(REQUIRED_RECORD_COUNT, (int) totalCount), hobby,
					null, null, null, false, false);
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
				return new Basis(AiCompareResponse.lockedNoPastRecord(REQUIRED_RECORD_COUNT, (int) totalCount,
						oldestDate, current.getRecordDate()), hobby, null, null, null, false, false);
			}
		}
		boolean anchoredToLatest = !baseDate.equals(LocalDate.now(SERVER_ZONE));
		return new Basis(null, hobby, past, current, baseDate, pastIsOldest, anchoredToLatest);
	}

	/**
	 * 변화 탭의 기본 데이터(이전·최근 기록과 숫자). AI를 부르지 않아 즉시 답하고, 해석(compare)과 같은 기록 선정·같은 숫자를 쓴다.
	 */
	@Transactional(readOnly = true)
	public CompareBasisResponse basis(Long userId, CompareRequest request) {
		Basis basis = resolveBasis(userId, request);
		if (basis.locked() != null) {
			AiCompareResponse locked = basis.locked();
			return CompareBasisResponse.locked(locked.lockReason(), locked.requiredCount(), locked.currentCount(),
					locked.oldestRecordDate(), locked.latestRecordDate());
		}
		CompareMetrics metrics = computeMetrics(userId, basis.hobby().getId(), basis.past().getRecordDate(),
				basis.baseDate());
		return new CompareBasisResponse(false, null, null, null, null, null, basis.baseDate(), basis.anchoredToLatest(),
				basis.pastIsOldest(), basis.past().getRecordDate(), basis.current().getRecordDate(),
				toCard(basis.past()), toCard(basis.current()), metrics);
	}

	@Transactional
	public AiCompareResponse compare(Long userId, CompareRequest request) {
		Basis basis = resolveBasis(userId, request);
		if (basis.locked() != null) {
			return basis.locked();
		}
		Hobby hobby = basis.hobby();
		ActivityRecord past = basis.past();
		ActivityRecord current = basis.current();
		LocalDate baseDate = basis.baseDate();
		boolean pastIsOldest = basis.pastIsOldest();
		boolean anchoredToLatest = basis.anchoredToLatest();

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
		// (하루 한도에 세지 않는다), 그래도 안 되면 예전 한 문단 방식으로 대체한다. 구조화 결과가 나왔는데 gap·next가 비면
		// 그 구역을 반드시 채우라는 보강 지시로 한 번 더 부르고(총 3번 이하), 그래도 비면 서버가 정한 문장으로 채운다.
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
		} else {
			if (stored.gap().isEmpty() || stored.next().isEmpty()) {
				stored = retryMissingSections(userId, systemPrompt, payload, schema, stored, check.dropNote(), past, current);
			}
			stored = fillMissingSections(stored, current);
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

	/** gap 또는 next가 비었을 때 한 번만 더 불러 빈 구역만 채운다(하루 한도 미집계). 호출이 실패해도 결과 전체를 버리지 않는다. */
	private StoredCompareResult retryMissingSections(Long userId, String systemPrompt, ComparePayload payload,
			java.util.Map<String, Object> schema, StoredCompareResult stored, String dropNote, ActivityRecord past,
			ActivityRecord current) {
		String hint = systemPrompt + "\n\n[보강 지시] '더 키워 볼 점(gap)'과 '이렇게 해 보면 좋아요(next)'를 각각 1~2개 반드시 포함해 "
				+ "JSON 객체 하나만 다시 응답해요. " + (dropNote == null || dropNote.isBlank() ? "" : dropNote);
		StoredCompareResult second = null;
		try {
			AiCallResult again = aiCallService.call(userId, AiResultType.COMPARE, hint, payload, true,
					STRUCTURED_SCHEMA_NAME, schema);
			second = validateStructured(again.content(), past, current).stored();
		} catch (ApiException e) {
			log.warn("COMPARE_SECTION_RETRY_FAILED code={}", e.getCode());
		}
		List<StoredCompareInsight> gap = stored.gap().isEmpty() && second != null ? second.gap() : stored.gap();
		List<StoredCompareIdea> next = stored.next().isEmpty() && second != null ? second.next() : stored.next();
		return withSections(stored, gap, next);
	}

	private StoredCompareResult withSections(StoredCompareResult base, List<StoredCompareInsight> gap,
			List<StoredCompareIdea> next) {
		return new StoredCompareResult(base.interpretation(), base.pastRecordId(), base.currentRecordId(), base.metrics(),
				base.baseDate(), base.anchoredToLatest(), base.pastIsOldest(), base.schemaVersion(), base.headline(),
				base.good(), gap, next);
	}

	/** 재호출 뒤에도 gap·next가 비면 AI 없이 기록에서 정한 문장으로 채운다(근거는 최근 기록). */
	private StoredCompareResult fillMissingSections(StoredCompareResult stored, ActivityRecord current) {
		List<StoredCompareInsight> gap = stored.gap();
		List<StoredCompareIdea> next = stored.next();
		if (gap.isEmpty()) {
			String difficulty = current.getDifficulty() == null ? "" : current.getDifficulty().trim();
			if (!difficulty.isEmpty()) {
				String snippet = difficulty.length() > FALLBACK_DIFFICULTY_MAX ? difficulty.substring(0, FALLBACK_DIFFICULTY_MAX)
						: difficulty;
				log.warn("COMPARE_SECTION_FALLBACK section=gap source=DIFFICULTY");
				gap = List.of(new StoredCompareInsight("기록에 적은 '" + snippet + "'는 아직 남은 숙제예요",
						"최근 기록에 적은 막힌 점이에요", List.of(current.getId())));
			} else {
				log.warn("COMPARE_SECTION_FALLBACK section=gap source=FEW_RECORDS");
				gap = List.of(new StoredCompareInsight("비교할 기록이 아직 적어요", "기록이 더 쌓이면 변화가 더 선명해져요",
						List.of(current.getId())));
			}
		}
		if (next.isEmpty()) {
			String actionText = nextActionText(current);
			String firstTag = extractTags(current.getAiAnalysis()).stream().findFirst().orElse(null);
			String idea;
			String source;
			if (actionText != null) {
				idea = actionText;
				source = "NEXT_ACTION";
			} else if (firstTag != null) {
				idea = "'" + firstTag + "'를 한 가지 조건만 바꿔 한 번 더 해 보고 기록해 봐요";
				source = "TAG";
			} else {
				idea = "지금 하던 것을 한 가지 조건만 바꿔 한 번 더 해 보고 기록해 봐요";
				source = "GENERIC";
			}
			log.warn("COMPARE_SECTION_FALLBACK section=next source={}", source);
			next = List.of(new StoredCompareIdea(idea, List.of(current.getId())));
		}
		return withSections(stored, gap, next);
	}

	/** 기록 분석에 저장된 "다음에 해 볼 일" 문장. 없거나 읽을 수 없으면 null. */
	private String nextActionText(ActivityRecord record) {
		if (record.getAiAnalysis() == null) {
			return null;
		}
		try {
			JsonNode text = objectMapper.readTree(record.getAiAnalysis()).path("nextAction").path("text");
			return text.isTextual() && !text.asText().isBlank() ? text.asText().trim() : null;
		} catch (JsonProcessingException e) {
			return null;
		}
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

	/** 구조화 검증 결과. stored가 null이면 전체 실패이고 field·reason이 첫 실패 지점이다. dropNote는 버려진 항목을 한 줄로 요약한 것이다. */
	record StructuredCheck(StoredCompareResult stored, String field, String reason, String dropNote) {
		static StructuredCheck ok(StoredCompareResult stored, String dropNote) {
			return new StructuredCheck(stored, null, null, dropNote);
		}

		static StructuredCheck failed(String field, String reason) {
			return new StructuredCheck(null, field, reason, null);
		}
	}

	/** 한 문장 검사 결과: value가 null이면 reason(LENGTH|FIELD|FORBIDDEN|NUMBER)이 거절 이유다. matched는 걸린 금지어나 숫자다. */
	private record Text(String value, String reason, int length, String matched, String snippet) {
		static Text rejected(String reason, int length, String matched, String text) {
			return new Text(null, reason, length, matched, text == null ? "-" : text.replaceAll("\\s+", " ").trim());
		}
	}

	/** 숫자 검사 방식: 엄격(거절), 소프트(통과하고 경고 로그), 없음. */
	private enum NumberMode {
		STRICT, SOFT, NONE
	}

	/** 항목 하나가 버려질 때 첫 사유를 field별로 기억해 두었다가, 그 field가 통째로 비면 보고하고, 재호출 지시에 쓸 한 줄도 모은다. */
	private static final class Drops {
		private final java.util.Map<String, String> firstReason = new java.util.HashMap<>();
		private final List<String> notes = new ArrayList<>();

		void add(String field, int item, Text text) {
			firstReason.putIfAbsent(field, text.reason());
			String matched = text.matched() == null ? "" : "(" + text.matched() + ")";
			notes.add(field + " " + (item + 1) + "번째 항목이 " + reasonText(text.reason()) + matched);
		}

		String reasonOf(String field) {
			return firstReason.getOrDefault(field, "FIELD");
		}

		void addEmpty(String field) {
			notes.add(field + " 구역이 비어 있");
		}

		/** "직전 응답에서는 …해서 거절됐어요" 한 줄. 모인 게 없으면 빈 문자열. */
		String note() {
			return notes.isEmpty() ? "" : "직전 응답에서는 " + String.join(", ", notes) + "었어요.";
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
			logDropped(recordIds, "headline", -1, Text.rejected("PARSE", rawJson == null ? 0 : rawJson.length(), null, null), rawJson);
			return StructuredCheck.failed("headline", "PARSE");
		}
		Set<Long> allowed = Set.of(past.getId(), current.getId());
		Set<Integer> allowedNumbers = allowedNumbers(past, current);
		Drops drops = new Drops();

		Text headline = cleanText(node.path("headline"), HEADLINE_HARD_MAX, allowedNumbers, true, NumberMode.STRICT, "headline");
		if (headline.value() == null) {
			logDropped(recordIds, "headline", -1, headline, rawJson);
			return StructuredCheck.failed("headline", headline.reason());
		}
		List<StoredCompareInsight> good = insights("good", node.path("good"), GOOD_MAX, allowed, current, allowedNumbers,
				recordIds, rawJson, drops, true, NumberMode.STRICT);
		if (good.isEmpty()) {
			String reason = node.path("good").isArray() ? drops.reasonOf("good") : "FIELD";
			if (!node.path("good").isArray()) {
				logDropped(recordIds, "good", -1, Text.rejected("FIELD", 0, null, null), rawJson);
			}
			return StructuredCheck.failed("good", reason);
		}
		List<StoredCompareInsight> gap = insights("gap", node.path("gap"), GAP_MAX, allowed, current, allowedNumbers,
				recordIds, rawJson, drops, false, NumberMode.SOFT);
		List<StoredCompareIdea> next = ideas(node.path("next"), NEXT_MAX, allowed, current, allowedNumbers, recordIds,
				rawJson, drops);
		if (gap.isEmpty()) {
			drops.addEmpty("gap");
		}
		if (next.isEmpty()) {
			drops.addEmpty("next");
		}
		log.info("COMPARE_STRUCT_OK good={} gap={} next={}", good.size(), gap.size(), next.size());
		// 구조화 결과는 비교한 두 기록을 항상 함께 보여 준다(근거로 어느 쪽을 몇 번 썼는지와 상관없이).
		return StructuredCheck.ok(new StoredCompareResult(headline.value(), past.getId(), current.getId(), null, null,
				false, false, STRUCTURED_SCHEMA_VERSION, headline.value(), good, gap, next), drops.note());
	}

	private List<StoredCompareInsight> insights(String field, JsonNode arrayNode, int max, Set<Long> allowed,
			ActivityRecord current, Set<Integer> allowedNumbers, List<Long> recordIds, String rawJson, Drops drops,
			boolean applyB, NumberMode numberMode) {
		List<StoredCompareInsight> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		int index = -1;
		for (JsonNode item : arrayNode) {
			index++;
			if (kept.size() >= max) {
				break;
			}
			Text point = cleanText(item.path("point"), ITEM_HARD_MAX, allowedNumbers, applyB, numberMode, field + "." + index);
			if (point.value() == null) {
				drop(field, index, point, recordIds, rawJson, drops);
				continue;
			}
			// 설명 끝에 평가 한 마디("좋아요" 등)만 덧붙은 경우는 검사 전에 그 마디만 잘라낸다.
			JsonNode becauseNode = item.path("because");
			if (becauseNode.isTextual()) {
				becauseNode = objectMapper.getNodeFactory().textNode(trimPraiseTail(becauseNode.asText()));
			}
			Text because = cleanText(becauseNode, ITEM_HARD_MAX, allowedNumbers, applyB, numberMode, field + "." + index);
			if (because.value() == null) {
				drop(field, index, because, recordIds, rawJson, drops);
				continue;
			}
			kept.add(new StoredCompareInsight(point.value(), because.value(),
					evidenceIds(item.path("evidenceRecordIds"), allowed, current)));
		}
		return kept;
	}

	private List<StoredCompareIdea> ideas(JsonNode arrayNode, int max, Set<Long> allowed, ActivityRecord current,
			Set<Integer> allowedNumbers, List<Long> recordIds, String rawJson, Drops drops) {
		List<StoredCompareIdea> kept = new ArrayList<>();
		if (!arrayNode.isArray()) {
			return kept;
		}
		int index = -1;
		for (JsonNode item : arrayNode) {
			index++;
			if (kept.size() >= max) {
				break;
			}
			// next는 새 숫자(속도·횟수 등 제안)를 쓸 수 있어 숫자 검사를 하지 않는다.
			Text idea = cleanText(item.path("idea"), ITEM_HARD_MAX, allowedNumbers, true, NumberMode.NONE, "next." + index);
			if (idea.value() == null) {
				drop("next", index, idea, recordIds, rawJson, drops);
				continue;
			}
			kept.add(new StoredCompareIdea(idea.value(), evidenceIds(item.path("evidenceRecordIds"), allowed, current)));
		}
		return kept;
	}

	/** 마지막 문장이 8자 이하이고 평가 단어뿐이면 그 문장만 잘라낸다. 문장이 하나뿐이거나 그 밖의 문장은 그대로 둔다. */
	static String trimPraiseTail(String because) {
		String text = because.trim();
		int cut = -1;
		for (int i = text.length() - 2; i >= 0; i--) {
			char ch = text.charAt(i);
			if (ch == '.' || ch == '!' || ch == '?') {
				cut = i;
				break;
			}
		}
		if (cut < 0) {
			return text;
		}
		String tail = text.substring(cut + 1).trim().replaceAll("[.!?\\s]+$", "");
		if (tail.length() <= 8 && PRAISE_TAIL.matcher(tail).matches()) {
			return text.substring(0, cut + 1).trim();
		}
		return text;
	}

	private void drop(String field, int item, Text text, List<Long> recordIds, String rawJson, Drops drops) {
		drops.add(field, item, text);
		logDropped(recordIds, field, item, text, rawJson);
	}

	private void logDropped(List<Long> recordIds, String field, int item, Text text, String rawJson) {
		String raw = rawJson == null ? "" : rawJson.replaceAll("\\s+", " ");
		if (raw.length() > RAW_LOG_MAX) {
			raw = raw.substring(0, RAW_LOG_MAX);
		}
		String snippet = text.snippet() == null ? "-" : text.snippet();
		if (snippet.length() > LOG_TEXT_MAX) {
			snippet = snippet.substring(0, LOG_TEXT_MAX);
		}
		log.warn("COMPARE_STRUCT_DROPPED recordIds={} field={} reason={} length={} item={} text={} matched={} raw={}",
				recordIds, field, text.reason(), text.length(), item < 0 ? "-" : String.valueOf(item), snippet,
				text.matched() == null ? "-" : text.matched(), raw);
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

	/**
	 * 문자열이 아니거나 비었으면 FIELD, 길이를 넘으면 LENGTH, 금지어면 FORBIDDEN, 기록에 없는 숫자가 있으면 NUMBER(엄격할 때).
	 * applyB가 false면 금지어 B(과장·예측)는 보지 않는다. 소프트 숫자 검사는 통과시키되 COMPARE_NUMBER_SOFT 경고만 남긴다.
	 */
	private Text cleanText(JsonNode node, int maxLength, Set<Integer> allowedNumbers, boolean applyB, NumberMode numberMode,
			String where) {
		if (!node.isTextual()) {
			return Text.rejected("FIELD", 0, null, null);
		}
		String text = node.asText().trim();
		if (text.isEmpty()) {
			return Text.rejected("FIELD", 0, null, null);
		}
		if (text.length() > maxLength) {
			return Text.rejected("LENGTH", text.length(), null, text);
		}
		String forbidden = firstForbidden(text, applyB);
		if (forbidden != null) {
			return Text.rejected("FORBIDDEN", text.length(), forbidden, text);
		}
		if (numberMode != NumberMode.NONE) {
			for (int number : numbersIn(text)) {
				// 한·두(1·2)는 "한 번", "두 기록"처럼 숫자가 아닌 말로도 흔히 쓰여 검사하지 않는다.
				if (number > 2 && !allowedNumbers.contains(number)) {
					if (numberMode == NumberMode.STRICT) {
						return Text.rejected("NUMBER", text.length(), String.valueOf(number), text);
					}
					log.warn("COMPARE_NUMBER_SOFT where={} number={} text={}", where, number,
							text.length() > LOG_TEXT_MAX ? text.substring(0, LOG_TEXT_MAX) : text);
				}
			}
		}
		return new Text(text, null, text.length(), null, null);
	}

	/** 걸린 금지어를 돌려준다(없으면 null). A는 항상, B는 applyB일 때만 본다. */
	private static String firstForbidden(String text, boolean applyB) {
		for (String word : FORBIDDEN_A) {
			if (text.contains(word)) {
				return word;
			}
		}
		if (applyB) {
			for (String word : FORBIDDEN_B) {
				if (text.contains(word)) {
					return word;
				}
			}
		}
		return null;
	}

	/** 입력(두 기록의 본문·어려움·시간·날짜)에 나온 숫자 전부(아라비아 숫자와 한글 수사). */
	private Set<Integer> allowedNumbers(ActivityRecord past, ActivityRecord current) {
		Set<Integer> numbers = new java.util.HashSet<>();
		Set<Integer> content = new java.util.HashSet<>();
		for (ActivityRecord record : List.of(past, current)) {
			content.addAll(numbersIn(record.getContent()));
			content.addAll(numbersIn(record.getDifficulty()));
			if (record.getMinutes() != null) {
				content.add(record.getMinutes());
			}
			numbers.add(record.getRecordDate().getYear());
			numbers.add(record.getRecordDate().getMonthValue());
			numbers.add(record.getRecordDate().getDayOfMonth());
		}
		numbers.addAll(content);
		// 본문·어려움·시간에 나온 숫자 두 개의 차와 합도 인정한다("20분에서 30분으로 10분 늘렸다" 같은 계산). 날짜 숫자는 제외한다.
		List<Integer> base = new ArrayList<>(content);
		for (int i = 0; i < base.size(); i++) {
			for (int j = i + 1; j < base.size(); j++) {
				numbers.add(Math.abs(base.get(i) - base.get(j)));
				long sum = (long) base.get(i) + base.get(j);
				if (sum <= Integer.MAX_VALUE) {
					numbers.add((int) sum);
				}
			}
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

	/** 예전 한 문단(interpretation)은 두 금지어 목록을 모두 적용한다. */
	private boolean containsForbiddenWord(String text) {
		return firstForbidden(text, true) != null;
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
