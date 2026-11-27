package com.growit.record.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growit.ai.entity.AiLog;
import com.growit.ai.entity.AiLogState;
import com.growit.ai.entity.AiResultType;
import com.growit.ai.repository.AiLogRepository;
import com.growit.ai.service.AiCallResult;
import com.growit.ai.service.AiCallService;
import com.growit.ai.service.AiLimitException;
import com.growit.ai.service.AiProviderDownException;
import com.growit.ai.service.AiResponseValidator;
import com.growit.common.exception.ApiException;
import com.growit.common.exception.ErrorCode;
import com.growit.common.text.InputWords;
import com.growit.hobby.entity.Hobby;
import com.growit.hobby.repository.HobbyRepository;
import com.growit.record.entity.ActivityRecord;
import com.growit.record.entity.AiStatus;
import com.growit.record.repository.ActivityRecordRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RecordAnalysisService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RecordAnalysisService.class);
	private static final int RAW_LOG_MAX = 200;

	private static final int HISTORY_LIMIT = 5;
	private static final int HISTORY_CONTENT_MAX = 300;
	private static final int HISTORY_DIFFICULTY_MAX = 200;
	private static final int TITLE_MAX = 20;
	private static final int TAG_MAX = 12;
	private static final int TAGS_MAX_COUNT = 3;
	private static final int RELATED_MAX_COUNT = 2;
	private static final int CHANGE_TEXT_MAX = 90;
	private static final int SUPPORT_TEXT_MAX = 80;
	private static final int NEXT_ACTION_TEXT_MAX = 80;
	private static final int NEXT_ACTION_MIN_MINUTES = 5;
	private static final int NEXT_ACTION_MAX_MINUTES = 10;
	/**
	 * 접근 제한자를 package-private으로 완화함(E1 AI 품질 평가 하네스가 같은 패키지의 테스트에서 재사용).
	 * "잘했", "훌륭", "대단"은 뺐다 — 과정에 대한 구체적인 인정·응원은 허용하고, 근거 없는 일반 칭찬이 되지 않게
	 * 프롬프트가 제한한다.
	 */
	static final List<String> FORBIDDEN_WORDS = List.of(
			// 능력 단정·점수화
			"실력", "늘었", "향상", "점수", "순위",
			// 과장
			"완벽", "최고", "천재",
			// 단정적 예측
			"틀림없", "될 거예요", "될 거에요");
	/** 다음 행동에는 재촉하는 말도 막는다(기록 해석의 금지어에 더해). */
	static final List<String> URGENCY_WORDS = List.of("꼭", "반드시", "해야", "빨리");
	/** 아무 기록에나 맞는 뻔한 제안 표현. 프롬프트가 막지만 서버도 한 번 더 거른다. */
	static final List<String> GENERIC_PHRASES =
			List.of("다시 연습", "복습", "꾸준히", "자신 있는 곳", "한 번 더 연습", "한 번 더 해");
	private static final int NEXT_ACTION_REASON_MAX = 40;
	/** 강한 칭찬 표현. 어려움을 넘겼거나 새로 해 본 일, 뚜렷한 진전이 있을 때만 어울려, 평범한 기록에서는 과한 칭찬으로 본다. */
	static final List<String> WARM_STRONG = List.of("멋져요", "대단해요", "잘하셨어요", "잘했어요");
	/** 부드러운 따뜻한 표현(공감·가벼운 인정). 평범한 기록에도 쓸 수 있다. */
	static final List<String> WARM_SOFT = List.of("수고했어요", "고생했어요", "애썼어요", "괜찮아요", "충분해요", "응원해요", "다행이에요",
			"좋았겠어요", "좋아 보여요", "편안했겠어요", "반가워요", "좋은 시작", "좋은 흐름", "기분 좋은", "잘 이어 가고 있어요");
	/** support에는 이 합집합(강함+부드러움) 중 하나 이상이 있어야 한다. 문구를 늘릴 때는 위 두 목록만 고친다. */
	static final List<String> WARM_PHRASES = java.util.stream.Stream.concat(WARM_STRONG.stream(), WARM_SOFT.stream())
			.toList();
	/** support에 쓰면 안 되는 강조어(하드). 칭찬이 부풀려 보이는 것을 막는다. */
	static final List<String> EMPHASIS_WORDS = List.of("정말", "너무", "진짜", "엄청", "굉장히");
	/** 평범한 기록(어려움 없음, 감정이 힘듦 아님)의 support가 강한 칭찬을 쓸 때 재호출에 붙이는 교정 문구. */
	static final String OVERPRAISE_CORRECTION = "평범한 기록이므로 강한 칭찬 대신 공감이나 가벼운 인정 한 문장으로 써요";
	/** 보고서처럼 관찰하는 문체. support와 change 모두에서 막는다. */
	static final List<String> REPORT_STYLE_PHRASES = List.of("알 수 있어요", "알 수 있습니다", "주목해요", "기억해요", "느껴져요",
			"잘 드러", "과정이 보여", "점이에요", "기록이에요", "보여요");
	static final String FALLBACK_SUPPORT_HARD = "힘든 날에도 기록을 남겼네요. 오늘은 이걸로 충분해요. 고생했어요.";
	static final String FALLBACK_SUPPORT_GOOD = "오늘도 기록을 이어 갔네요. 좋은 흐름이에요.";
	static final String FALLBACK_SUPPORT_NORMAL = "오늘도 한 줄 남겼네요. 이어 가는 것만으로 충분해요.";
	private static final String PROMPT_RESOURCE_NAME = "record-analysis-system.txt";
	/** prompts/record-analysis-system.txt의 내용을 바꿀 때마다 올린다. 저장된 결과가 어떤 지시문으로 만들어졌는지 추적하기 위해서다. */
	static final int PROMPT_VERSION = 12;

	private final ActivityRecordRepository activityRecordRepository;
	private final HobbyRepository hobbyRepository;
	private final AiCallService aiCallService;
	private final AiResponseValidator aiResponseValidator;
	private final AiLogRepository aiLogRepository;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	/** 패키지 안 테스트에서만 다른 리소스 이름으로 바꿔 끼워 읽기 실패를 재현한다. */
	SystemPromptLoader promptLoader = new SystemPromptLoader(PROMPT_RESOURCE_NAME);

	/**
	 * true면 구체성 검사(입력 낱말·일반 표현)에 걸린 제안을 버린다. 기본은 완화(false)라서 로그(NEXT_ACTION_SOFT)만
	 * 남기고 통과시킨다. 환경변수 AI_NEXT_ACTION_STRICT=true로 켠다.
	 */
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

	@Autowired(required = false)
	void setRoadmapContextSource(RoadmapContextSource source) {
		this.roadmapContextSource = source;
	}

	/** FAILED 상태의 ai_analysis에 넣는 값. 한도로 건너뛴 경우에만 있고, 예전 FAILED 행은 비어 있다. */
	static final String FAIL_REASON_LIMIT = "LIMIT";
	static final String FAIL_REASON_PROVIDER_DOWN = "PROVIDER_DOWN";

	boolean strictSpecificity = "true".equalsIgnoreCase(System.getenv("AI_NEXT_ACTION_STRICT"));

	public RecordAnalysisService(ActivityRecordRepository activityRecordRepository, HobbyRepository hobbyRepository,
			AiCallService aiCallService, AiResponseValidator aiResponseValidator, AiLogRepository aiLogRepository,
			ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
		this.activityRecordRepository = activityRecordRepository;
		this.hobbyRepository = hobbyRepository;
		this.aiCallService = aiCallService;
		this.aiResponseValidator = aiResponseValidator;
		this.aiLogRepository = aiLogRepository;
		this.objectMapper = objectMapper;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * AI 호출(네트워크 I/O)은 트랜잭션 밖에서 한다. DB 조회·저장만 각각 짧은 트랜잭션으로 묶어, AI 응답을
	 * 기다리는 동안 커넥션을 붙들고 있지 않게 했다. 분석 시작 전과 저장 직전 모두 상태가 PENDING인지 다시
	 * 확인해, 그 사이 기록이 수정·삭제되었으면 조용히 건너뛴다.
	 */
	public void analyze(Long recordId, Long userId) {
		AnalysisInput input;
		try {
			input = transactionTemplate.execute(status -> prepareInput(recordId, userId));
		} catch (RuntimeException e) {
			transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
			return;
		}
		if (input == null) {
			return;
		}

		String systemPrompt;
		try {
			systemPrompt = promptLoader.load();
		} catch (IOException e) {
			aiLogRepository.save(new AiLog(userId, AiResultType.ANALYSIS.name(), AiLogState.FAIL.name(), null, null,
					"프롬프트 파일을 읽지 못함"));
			transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
			return;
		}

		StoredAnalysis analysis;
		String failReason = null;
		try {
			AiCallResult result = aiCallService.call(userId, AiResultType.ANALYSIS, systemPrompt, input.payload());
			ValidationOutcome outcome = validateOutcome(result.content(), input.allowedHistoryIds(), result.model(),
					input.recordId(), input.corpus(), input.roadmapItemIds(), input.payload().record());
			if (outcome.supportIssue() != null) {
				outcome = retryForSupport(userId, systemPrompt, input, outcome);
			}
			analysis = outcome.supportIssue() == null ? outcome.analysis() : withFallbackSupport(outcome, input);
		} catch (AiLimitException e) {
			// 하루 한도 때문에 건너뛴 것은 화면에서 "한도"로 따로 안내하려고 실패 이유를 남긴다.
			analysis = null;
			failReason = FAIL_REASON_LIMIT;
		} catch (AiProviderDownException e) {
			analysis = null;
			failReason = FAIL_REASON_PROVIDER_DOWN;
		} catch (RuntimeException e) {
			analysis = null;
		}

		StoredAnalysis finalAnalysis = analysis;
		String finalFailReason = failReason;
		transactionTemplate.executeWithoutResult(status -> applyResult(recordId, userId, finalAnalysis, finalFailReason));
	}

	/**
	 * support가 규칙을 어겼을 때만 같은 분석을 교정 지시와 함께 한 번 더 부른다. 다시 불러도 실패하거나 호출 자체가
	 * 막히면(한도 등) 처음 결과를 그대로 두고 support만 기본 문장으로 채운다.
	 */
	private ValidationOutcome retryForSupport(Long userId, String systemPrompt, AnalysisInput input,
			ValidationOutcome first) {
		log.warn("SUPPORT_RETRY recordId={} reason={}", input.recordId(), first.supportIssue());
		String corrected = systemPrompt + "\n\n[교정 지시] 직전 응답의 support가 규칙을 어겼어요: " + first.supportIssue()
				+ ". 같은 입력으로 규칙을 지켜 JSON 객체 하나만 다시 응답해요.";
		try {
			AiCallResult result = aiCallService.call(userId, AiResultType.ANALYSIS, corrected, input.payload(), true);
			ValidationOutcome second = validateOutcome(result.content(), input.allowedHistoryIds(), result.model(),
					input.recordId(), input.corpus(), input.roadmapItemIds(), input.payload().record());
			if (second.supportIssue() == null) {
				return second;
			}
			// 과한 칭찬은 문장 자체는 규칙에 맞으므로, 다시 불러도 같으면 그대로 두고 경고만 남긴다.
			if (second.overpraise()) {
				return acceptedWithOverpraise(second, input);
			}
			return first.overpraise() ? acceptedWithOverpraise(first, input) : first;
		} catch (RuntimeException e) {
			return first.overpraise() ? acceptedWithOverpraise(first, input) : first;
		}
	}

	private ValidationOutcome acceptedWithOverpraise(ValidationOutcome outcome, AnalysisInput input) {
		log.warn("SUPPORT_OVERPRAISE recordId={}", input.recordId());
		return new ValidationOutcome(outcome.analysis(), null, outcome.evidenceId(), false);
	}

	private StoredAnalysis withFallbackSupport(ValidationOutcome outcome, AnalysisInput input) {
		AnalysisRecordInput record = input.payload().record();
		boolean hard = "HARD".equals(record.feelingDirection())
				|| (record.difficulty() != null && !record.difficulty().isBlank());
		String support = hard ? FALLBACK_SUPPORT_HARD
				: "GOOD".equals(record.feelingDirection()) ? FALLBACK_SUPPORT_GOOD : FALLBACK_SUPPORT_NORMAL;
		log.warn("SUPPORT_FALLBACK recordId={} reason={}", input.recordId(), outcome.supportIssue());
		StoredAnalysis a = outcome.analysis();
		String text = a.change() == null ? null : a.change().text();
		StoredChange change = new StoredChange(text, outcome.evidenceId(), support);
		return new StoredAnalysis(a.title(), a.tags(), a.relatedRecordIds(), change, a.tagsFeedback(),
				a.changeFeedback(), a.promptVersion(), a.model(), a.nextAction(), a.roadmapSuggestion());
	}

	/** supportIssue가 null이 아니면 support가 규칙을 어긴 것이다(evidenceId는 그때 기본 문장을 붙일 근거 기록). */
	private record ValidationOutcome(StoredAnalysis analysis, String supportIssue, Long evidenceId,
			boolean overpraise) {
	}

	/** overpraise가 true이면 supportIssue는 "과한 칭찬"뿐이고 support 문장 자체는 저장 가능한 상태다. */
	private record ChangeOutcome(StoredChange change, String supportIssue, Long evidenceId, boolean overpraise) {
	}

	/** FAILED 기록의 실패 이유("LIMIT")를 읽는다. 예전 행처럼 값이 없거나 읽을 수 없으면 null. */
	public String failReasonOf(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			JsonNode reason = objectMapper.readTree(json).path("failReason");
			String value = reason.asText();
			return FAIL_REASON_LIMIT.equals(value) || FAIL_REASON_PROVIDER_DOWN.equals(value) ? value : null;
		} catch (Exception e) {
			return null;
		}
	}

	public void markQueueRejected(Long recordId, Long userId) {
		transactionTemplate.executeWithoutResult(status -> markFailedIfPending(recordId, userId));
	}

	private AnalysisInput prepareInput(Long recordId, Long userId) {
		ActivityRecord record = activityRecordRepository.findByIdAndUserId(recordId, userId).orElse(null);
		if (record == null || record.getAiStatus() != AiStatus.PENDING) {
			return null;
		}
		Hobby hobby = hobbyRepository.findById(record.getHobbyId()).orElseThrow();

		List<ActivityRecord> history = activityRecordRepository.findPrevious(userId, record.getHobbyId(),
				record.getRecordDate(), record.getId(), PageRequest.of(0, HISTORY_LIMIT));

		AnalysisRecordInput recordInput = new AnalysisRecordInput(
				record.getRecordDate().toString(),
				record.getContent(),
				record.getDifficulty(),
				// 직접 추가한 감정의 이름은 길이가 8자로 제한된 "자료"로만 넘긴다(지시로 읽히지 않게 프롬프트의 사용자 글 규칙을 따른다)
				feelingText(record),
				record.getMinutes(),
				record.getId(),
				feelingDirectionOf(record));
		List<AnalysisHistoryItem> historyItems = history.stream()
				.map(h -> new AnalysisHistoryItem(h.getId(), h.getRecordDate().toString(),
						truncate(h.getContent(), HISTORY_CONTENT_MAX),
						truncate(h.getDifficulty(), HISTORY_DIFFICULTY_MAX)))
				.toList();
		List<ActivityRecord> previousOfUser = activityRecordRepository.findPreviousOfUser(userId,
				record.getRecordDate(), record.getId(), PageRequest.of(0, 1));
		AnalysisContext context = previousOfUser.isEmpty()
				? new AnalysisContext(true, null)
				: new AnalysisContext(false, (int) java.time.temporal.ChronoUnit.DAYS
						.between(previousOfUser.get(0).getRecordDate(), record.getRecordDate()));
		RoadmapContextSource.RoadmapContext roadmapContext = roadmapContextSource.currentContext(userId,
				record.getHobbyId());
		AnalysisRoadmapInput roadmapInput = roadmapContext == null ? null
				: new AnalysisRoadmapInput(roadmapContext.stepTitle(), roadmapContext.items().stream()
						.map(item -> new AnalysisRoadmapInput.AnalysisRoadmapItem(item.id(), item.text())).toList());
		Set<Long> roadmapItemIds = roadmapContext == null ? Set.of()
				: roadmapContext.items().stream().map(RoadmapContextSource.RoadmapItemRef::id)
						.collect(java.util.stream.Collectors.toSet());
		AnalysisPayload payload = new AnalysisPayload(
				new AnalysisHobbyInput(hobby.getName(), hobby.getGoal()), recordInput, historyItems, context,
				roadmapInput);

		Set<Long> allowedIds = history.stream().map(ActivityRecord::getId)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		return new AnalysisInput(payload, allowedIds, record.getId(), corpusOf(hobby, record, history),
				roadmapItemIds);
	}

	private void applyResult(Long recordId, Long userId, StoredAnalysis analysis, String failReason) {
		ActivityRecord record = activityRecordRepository.findByIdAndUserId(recordId, userId).orElse(null);
		if (record == null || record.getAiStatus() != AiStatus.PENDING) {
			return;
		}
		if (analysis == null) {
			if (failReason != null) {
				record.markAnalysisFailed("{\"failReason\":\"" + failReason + "\"}");
			} else {
				record.markAnalysisFailed();
			}
			return;
		}
		record.markAnalysisDone(toJson(analysis));
	}

	private void markFailedIfPending(Long recordId, Long userId) {
		activityRecordRepository.findByIdAndUserId(recordId, userId)
				.filter(r -> r.getAiStatus() == AiStatus.PENDING)
				.ifPresent(ActivityRecord::markAnalysisFailed);
	}

	/** 접근 제한자를 package-private으로 완화함(E1 AI 품질 평가 하네스가 같은 패키지의 테스트에서 재사용). */
	StoredAnalysis validate(String rawJson, Set<Long> allowedHistoryIds, String model) {
		return validate(rawJson, allowedHistoryIds, model, null, null);
	}

	/** corpus가 null이면 낱말 포함 검사는 건너뛴다(품질 평가 하네스처럼 입력 전문이 없는 호출). */
	StoredAnalysis validate(String rawJson, Set<Long> allowedHistoryIds, String model, Long recordId,
			String corpus) {
		return validateOutcome(rawJson, allowedHistoryIds, model, recordId, corpus, Set.of(), null).analysis();
	}

	private ValidationOutcome validateOutcome(String rawJson, Set<Long> allowedHistoryIds, String model,
			Long recordId, String corpus, Set<Long> roadmapItemIds, AnalysisRecordInput record) {
		JsonNode node = aiResponseValidator.parse(rawJson);
		aiResponseValidator.requireFields(node, "title", "tags", "relatedRecordIds");

		String title = sanitizeTitle(node.path("title"));
		List<String> tags = sanitizeTags(node.path("tags"));
		List<Long> relatedRecordIds = sanitizeRelatedIds(node.path("relatedRecordIds"), allowedHistoryIds);
		// 비교할 이전 기록이 없을 때는 이 기록 자신이 근거가 될 수 있다(그래서 이 기록의 id도 허용한다).
		Set<Long> nextActionEvidenceIds = new LinkedHashSet<>(allowedHistoryIds);
		if (recordId != null) {
			nextActionEvidenceIds.add(recordId);
		}
		ChangeOutcome changeOutcome = sanitizeChange(node.path("change"), nextActionEvidenceIds, recordId, record);

		// nextAction이 이상해도 나머지 결과는 살린다(전체 실패 금지).
		StoredNextAction nextAction = sanitizeNextAction(node.path("nextAction"), nextActionEvidenceIds, corpus,
				recordId);

		StoredRoadmapSuggestion suggestion = sanitizeRoadmapSuggestion(node.path("roadmapSuggestion"), roadmapItemIds,
				recordId);
		StoredAnalysis analysis = new StoredAnalysis(title, tags, relatedRecordIds, changeOutcome.change(), null, null,
				PROMPT_VERSION, model, nextAction, suggestion);
		return new ValidationOutcome(analysis, changeOutcome.supportIssue(), changeOutcome.evidenceId(),
				changeOutcome.overpraise());
	}

	/** 입력으로 준 미완료 항목의 id만 받는다(남의 것·이미 체크한 것·지어낸 id는 버린다). 자동 체크는 하지 않는다. */
	private StoredRoadmapSuggestion sanitizeRoadmapSuggestion(JsonNode node, Set<Long> allowedItemIds, Long recordId) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		JsonNode idNode = node.path("itemId");
		if (!idNode.isIntegralNumber() || !idNode.canConvertToLong() || !allowedItemIds.contains(idNode.asLong())) {
			log.warn("ROADMAP_SUGGESTION_DROPPED recordId={} raw={}", recordId, rawOf(node));
			return null;
		}
		return new StoredRoadmapSuggestion(idNode.asLong(), null);
	}

	private String sanitizeTitle(JsonNode titleNode) {
		if (!titleNode.isTextual()) {
			return null;
		}
		String title = titleNode.asText().trim();
		if (title.isEmpty() || title.length() > TITLE_MAX || containsForbiddenWord(title)) {
			return null;
		}
		return title;
	}

	private List<String> sanitizeTags(JsonNode tagsNode) {
		List<String> result = new ArrayList<>();
		if (!tagsNode.isArray()) {
			return result;
		}
		for (JsonNode tagNode : tagsNode) {
			if (!tagNode.isTextual()) {
				continue;
			}
			String tag = tagNode.asText().trim();
			if (tag.isEmpty() || tag.length() > TAG_MAX || containsForbiddenWord(tag)) {
				continue;
			}
			result.add(tag);
			if (result.size() == TAGS_MAX_COUNT) {
				break;
			}
		}
		return result;
	}

	private List<Long> sanitizeRelatedIds(JsonNode idsNode, Set<Long> allowedIds) {
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
			if (result.size() == RELATED_MAX_COUNT) {
				break;
			}
		}
		return result;
	}

	private ChangeOutcome sanitizeChange(JsonNode changeNode, Set<Long> allowedIds, Long recordId,
			AnalysisRecordInput record) {
		if (changeNode.isMissingNode() || changeNode.isNull() || !changeNode.isObject()) {
			// 객체 자체가 없으면 근거 기록도 없어 기본 문장을 붙일 수 없다. 로그만 남긴다.
			log.warn("SUPPORT_MISSING recordId={}", recordId);
			return new ChangeOutcome(null, null, null, false);
		}
		// 근거 id가 틀리면 어느 문장도 화면에 낼 수 없으므로 통째로 버린다.
		JsonNode evidenceNode = changeNode.path("evidenceRecordId");
		if (!evidenceNode.canConvertToLong() || !allowedIds.contains(evidenceNode.asLong())) {
			log.warn("CHANGE_DROPPED recordId={} field=change|support reason=EVIDENCE length=- raw={}", recordId,
					rawOf(changeNode));
			return new ChangeOutcome(null, null, null, false);
		}
		long evidenceId = evidenceNode.asLong();
		// 한쪽만 실패하면 그 필드만 버리고 나머지는 살린다.
		String text = sanitizeChangeField(changeNode.path("text"), "change", CHANGE_TEXT_MAX, recordId, false);
		JsonNode supportNode = changeNode.path("support");
		String support = sanitizeChangeField(supportNode, "support", SUPPORT_TEXT_MAX, recordId, true);
		String supportIssue = null;
		if (support == null) {
			supportIssue = lastDropReason.get() != null ? lastDropReason.get() : "support가 없음";
			if (supportNode.isMissingNode() || supportNode.isNull()) {
				log.warn("SUPPORT_MISSING recordId={}", recordId);
			}
		}
		lastDropReason.remove();
		boolean overpraise = false;
		if (support != null && isOverpraise(support, record)) {
			// 평범한 기록에 강한 칭찬: 문장은 살려 두고, 한 번 더 불러 보도록 이유만 남긴다.
			supportIssue = OVERPRAISE_CORRECTION;
			overpraise = true;
		}
		if (text == null && support == null) {
			return new ChangeOutcome(null, supportIssue, evidenceId, false);
		}
		return new ChangeOutcome(new StoredChange(text, evidenceId, support), supportIssue, evidenceId, overpraise);
	}

	/** 직전 sanitizeChangeField가 버린 이유를 교정 지시 문장으로 넘기는 용도(호출 스레드 안에서만 쓴다). */
	private final ThreadLocal<String> lastDropReason = new ThreadLocal<>();

	/** 형식·길이·금지어·관찰체를 검사해 통과하면 다듬은 글을, 아니면 null을 준다(버릴 때마다 필드와 이유를 로그로 남긴다). */
	private String sanitizeChangeField(JsonNode node, String field, int maxLength, Long recordId, boolean requireWarm) {
		lastDropReason.remove();
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (!node.isTextual()) {
			return dropField(recordId, field, "FORMAT", node, -1, field + "가 문자열이 아님");
		}
		String value = node.asText().trim();
		if (value.isEmpty()) {
			return dropField(recordId, field, "EMPTY", node, 0, field + "가 비어 있음");
		}
		if (value.length() > maxLength) {
			return dropField(recordId, field, "LENGTH", node, value.length(), field + "가 " + maxLength + "자를 넘음");
		}
		if (containsForbiddenWord(value)) {
			return dropField(recordId, field, "FORBIDDEN", node, value.length(), "능력 단정·과장·점수화·단정적 예측 표현을 씀");
		}
		// "좋아 보여요"처럼 따뜻한 표현 안에 든 "보여요"는 관찰체로 보지 않는다.
		String styleTarget = value;
		for (String warm : WARM_PHRASES) {
			styleTarget = styleTarget.replace(warm, "");
		}
		if (requireWarm && EMPHASIS_WORDS.stream().anyMatch(value::contains)) {
			return dropField(recordId, field, "EMPHASIS", node, value.length(),
					"강조어(" + String.join(", ", EMPHASIS_WORDS) + ")를 씀");
		}
		final String checkedForStyle = styleTarget;
		if (REPORT_STYLE_PHRASES.stream().anyMatch(checkedForStyle::contains)) {
			return dropField(recordId, field, "STYLE", node, value.length(),
					"관찰체·보고서체 표현(" + String.join(", ", REPORT_STYLE_PHRASES) + ")을 씀");
		}
		if (requireWarm && WARM_PHRASES.stream().noneMatch(value::contains)) {
			return dropField(recordId, field, "NOT_WARM", node, value.length(),
					"따뜻한 직접 표현(" + String.join(", ", WARM_PHRASES) + ") 중 하나가 없음");
		}
		return value;
	}

	/** 막힌 점(어려움)이 없고 감정 방향이 힘듦이 아닌 평범한 기록에서 support가 강한 칭찬을 쓰면 과한 칭찬이다. record가 없으면 검사하지 않는다. */
	private static boolean isOverpraise(String support, AnalysisRecordInput record) {
		if (record == null) {
			return false;
		}
		boolean heavy = (record.difficulty() != null && !record.difficulty().isBlank())
				|| "HARD".equals(record.feelingDirection());
		return !heavy && WARM_STRONG.stream().anyMatch(support::contains);
	}

	private String dropField(Long recordId, String field, String reason, JsonNode node, int length, String correction) {
		droppedField(recordId, field, reason, node, length);
		lastDropReason.set(correction);
		return null;
	}

	/** raw는 앞 150자만 남긴다(사용자 글 전체를 로그에 남기지 않기 위해). */
	private void droppedField(Long recordId, String field, String reason, JsonNode node, int length) {
		String raw = node.toString();
		log.warn("CHANGE_DROPPED recordId={} field={} reason={} length={} raw={}", recordId, field, reason, length,
				raw.length() > 150 ? raw.substring(0, 150) : raw);
	}

	/**
	 * 하드 검사(형식·길이·분 범위·금지어·재촉어·남의 기록 id)에 걸리면 nextAction만 버리고, 근거 id가 없거나
	 * 입력에 없으면 "이 기록"으로 대체한다. 구체성 검사는 기본적으로 로그만 남기고 통과시킨다. 버릴 때마다
	 * NEXT_ACTION_DROPPED 로그로 이유를 남겨 어디서 막히는지 운영에서 볼 수 있게 한다.
	 */
	private StoredNextAction sanitizeNextAction(JsonNode actionNode, Set<Long> allowedIds, String corpus,
			Long recordId) {
		if (actionNode.isMissingNode() || actionNode.isNull()) {
			return dropped(recordId, "MISSING", actionNode);
		}
		if (!actionNode.isObject()) {
			return dropped(recordId, "PARSE", actionNode);
		}
		JsonNode textNode = actionNode.path("text");
		JsonNode minutesNode = actionNode.path("minutes");
		JsonNode reasonNode = actionNode.path("reason");
		if (!textNode.isTextual() || !minutesNode.isIntegralNumber() || !minutesNode.canConvertToInt()
				|| !reasonNode.isTextual()) {
			return dropped(recordId, "FIELD", actionNode);
		}
		String text = textNode.asText().trim();
		String reason = reasonNode.asText().trim();
		int minutes = minutesNode.asInt();
		if (minutes < NEXT_ACTION_MIN_MINUTES || minutes > NEXT_ACTION_MAX_MINUTES) {
			return dropped(recordId, "RANGE", actionNode);
		}
		if (text.isEmpty() || text.length() > NEXT_ACTION_TEXT_MAX) {
			return dropped(recordId, "LENGTH", actionNode, "text=" + text.length() + "/" + NEXT_ACTION_TEXT_MAX);
		}
		if (reason.isEmpty() || reason.length() > NEXT_ACTION_REASON_MAX) {
			return dropped(recordId, "LENGTH", actionNode, "reason=" + reason.length() + "/" + NEXT_ACTION_REASON_MAX);
		}
		if (isForbiddenWording(text) || isForbiddenWording(reason)) {
			return dropped(recordId, "FORBIDDEN", actionNode);
		}

		JsonNode evidenceNode = actionNode.path("evidenceRecordId");
		Long evidenceId = evidenceNode.isIntegralNumber() && evidenceNode.canConvertToLong() ? evidenceNode.asLong()
				: null;
		if (evidenceId != null && !allowedIds.contains(evidenceId)) {
			if (isOtherUsersRecord(evidenceId, recordId)) {
				return dropped(recordId, "EVIDENCE", actionNode);
			}
			evidenceId = null;
		}
		if (evidenceId == null) {
			if (recordId == null) {
				return dropped(recordId, "EVIDENCE", actionNode);
			}
			log.info("NEXT_ACTION_EVIDENCE_SUBSTITUTED recordId={}", recordId);
			evidenceId = recordId;
		}

		if (corpus != null) {
			String soft = GENERIC_PHRASES.stream().anyMatch(text::contains) ? "GENERIC"
					: !InputWords.mentions(text, corpus) ? "NO_INPUT_WORD" : null;
			if (soft != null) {
				if (strictSpecificity) {
					return dropped(recordId, soft, actionNode);
				}
				log.warn("NEXT_ACTION_SOFT recordId={} reason={} raw={}", recordId, soft, rawOf(actionNode));
			}
		}
		return new StoredNextAction(text, minutes, null, reason, evidenceId);
	}

	private StoredNextAction dropped(Long recordId, String reason, JsonNode actionNode) {
		return dropped(recordId, reason, actionNode, "-");
	}

	/** detail은 어느 필드가 몇 자였는지 같은 보충(없으면 "-"). */
	private StoredNextAction dropped(Long recordId, String reason, JsonNode actionNode, String detail) {
		log.warn("NEXT_ACTION_DROPPED recordId={} reason={} detail={} raw={}", recordId, reason, detail,
				rawOf(actionNode));
		return null;
	}

	private String rawOf(JsonNode node) {
		String raw = node.isMissingNode() ? "" : node.toString();
		return raw.length() > RAW_LOG_MAX ? raw.substring(0, RAW_LOG_MAX) : raw;
	}

	/** 입력에 없는 id가 실제로 다른 사용자의 기록이면 true(그 경우는 대체하지 않고 버린다). */
	private boolean isOtherUsersRecord(Long evidenceId, Long recordId) {
		if (recordId == null) {
			return false;
		}
		Long ownerId = activityRecordRepository.findById(recordId).map(ActivityRecord::getUserId).orElse(null);
		return activityRecordRepository.findById(evidenceId)
				.map(other -> ownerId == null || !other.getUserId().equals(ownerId)).orElse(false);
	}

	private boolean isForbiddenWording(String text) {
		return containsForbiddenWord(text) || URGENCY_WORDS.stream().anyMatch(text::contains);
	}

	private String corpusOf(Hobby hobby, ActivityRecord record, List<ActivityRecord> history) {
		StringBuilder corpus = new StringBuilder();
		corpus.append(hobby.getName()).append(' ').append(nullToEmpty(hobby.getGoal())).append(' ')
				.append(record.getContent()).append(' ').append(nullToEmpty(record.getDifficulty()));
		for (ActivityRecord item : history) {
			corpus.append(' ').append(item.getContent()).append(' ').append(nullToEmpty(item.getDifficulty()));
		}
		return corpus.toString();
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String feelingDirectionOf(ActivityRecord record) {
		if (record.getFeeling() == null) {
			return record.getFeelingDirection();
		}
		return switch (record.getFeeling()) {
			case PROUD, RELIEVED -> "GOOD";
			case NEUTRAL -> "NEUTRAL";
			case STUCK -> "HARD";
		};
	}

	/** AI가 읽기 좋게 기본 감정은 한글 이름으로, 직접 추가한 감정은 이름 그대로 넘긴다(자료로만 쓰인다). */
	private String feelingText(ActivityRecord record) {
		if (record.getFeeling() == null) {
			return record.getFeelingLabel();
		}
		return switch (record.getFeeling()) {
			case PROUD -> "뿌듯함";
			case STUCK -> "답답함";
			case RELIEVED -> "개운함";
			case NEUTRAL -> "잘 모르겠음";
		};
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

	String toJson(StoredAnalysis analysis) {
		try {
			return objectMapper.writeValueAsString(analysis);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("분석 결과를 JSON으로 바꾸지 못함", e);
		}
	}

	StoredAnalysis readJson(String json) {
		try {
			return objectMapper.readValue(json, StoredAnalysis.class);
		} catch (JsonProcessingException e) {
			throw new ApiException(ErrorCode.ANALYSIS_NOT_READY, "저장된 분석을 읽지 못했어요");
		}
	}
}
